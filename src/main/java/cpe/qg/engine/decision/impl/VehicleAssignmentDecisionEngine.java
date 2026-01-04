package cpe.qg.engine.decision.impl;

import cpe.qg.engine.decision.api.DecisionDataSource;
import cpe.qg.engine.decision.api.DecisionEngine;
import cpe.qg.engine.decision.api.ScoredCandidate;
import cpe.qg.engine.decision.api.VehicleScoringStrategy;
import cpe.qg.engine.decision.model.DecisionCriteria;
import cpe.qg.engine.decision.model.DecisionResult;
import cpe.qg.engine.decision.model.VehicleAssignmentProposal;
import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.sdmis.dto.InterestPointRead;
import cpe.qg.engine.sdmis.dto.QGActivePhase;
import cpe.qg.engine.sdmis.dto.QGIncidentSituationRead;
import cpe.qg.engine.sdmis.dto.QGPhaseRequirements;
import cpe.qg.engine.sdmis.dto.QGPhaseTypeRef;
import cpe.qg.engine.sdmis.dto.QGRequirement;
import cpe.qg.engine.sdmis.dto.QGRequirementGroup;
import cpe.qg.engine.sdmis.dto.QGResourcePlanningRead;
import cpe.qg.engine.sdmis.dto.VehicleAssignmentRead;
import cpe.qg.engine.sdmis.dto.VehiclePositionLogRead;
import cpe.qg.engine.sdmis.dto.VehicleRead;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/** Decision engine that proposes vehicles for active incident phases. */
public final class VehicleAssignmentDecisionEngine implements DecisionEngine {

  private final DecisionDataSource dataSource;
  private final VehicleScoringStrategy scoringStrategy;
  private final DecisionCriteria criteria;
  private final RequirementAggregator requirementAggregator;
  private final Logger log = LoggerProvider.getLogger(VehicleAssignmentDecisionEngine.class);

  public VehicleAssignmentDecisionEngine(
      DecisionDataSource dataSource,
      VehicleScoringStrategy scoringStrategy,
      DecisionCriteria criteria) {
    this.dataSource = Objects.requireNonNull(dataSource, "Decision data source is required");
    this.scoringStrategy = Objects.requireNonNull(scoringStrategy, "Scoring strategy is required");
    this.criteria = criteria;
    this.requirementAggregator = new RequirementAggregator();
  }

  @Override
  public DecisionResult proposeAssignments(UUID incidentId) {
    Objects.requireNonNull(incidentId, "Incident id is required");
    try {
      QGIncidentSituationRead situation = dataSource.getIncidentSituation(incidentId);
      QGResourcePlanningRead planning = dataSource.getResourcePlanning(incidentId);

      GeoPosition incidentLocation = toIncidentPosition(situation);
      Map<UUID, QGActivePhase> phasesByType = selectPhasesByType(situation.phasesActive());
      Map<UUID, List<VehicleCandidate>> candidatePool =
          buildCandidatePool(planning, incidentLocation);

      Set<UUID> allocatedVehicles = new HashSet<>();
      Map<UUID, Integer> missingByType = new HashMap<>();
      List<VehicleAssignmentProposal> proposals = new ArrayList<>();

      List<QGPhaseRequirements> phaseRequirements =
          planning.phaseRequirements() == null ? List.of() : planning.phaseRequirements();

      for (QGPhaseRequirements phase : phaseRequirements) {
        if (phase.phaseType() == null || phase.phaseType().phaseTypeId() == null) {
          continue;
        }
        QGActivePhase selectedPhase = phasesByType.get(phase.phaseType().phaseTypeId());
        if (selectedPhase == null || selectedPhase.incidentPhaseId() == null) {
          continue;
        }
        List<QGRequirementGroup> groups = phase.groups() == null ? List.of() : phase.groups();
        List<QGRequirementGroup> orderedGroups = new ArrayList<>(groups);
        orderedGroups.sort(Comparator.comparing(this::groupPriorityKey));

        for (QGRequirementGroup group : orderedGroups) {
          Map<UUID, Integer> needed = requirementAggregator.aggregateGroup(group);
          for (Map.Entry<UUID, Integer> entry : needed.entrySet()) {
            UUID vehicleTypeId = entry.getKey();
            int count = entry.getValue();
            if (count <= 0) {
              continue;
            }
            List<VehicleCandidate> candidates =
                candidatePool.getOrDefault(vehicleTypeId, List.of());
            int selected =
                selectCandidates(
                    candidates,
                    allocatedVehicles,
                    proposals,
                    incidentId,
                    selectedPhase,
                    phase.phaseType(),
                    count);
            if (selected < count) {
              missingByType.merge(vehicleTypeId, count - selected, Integer::sum);
            }
          }
        }
      }

      return new DecisionResult(proposals, missingByType);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch decision data from SDMIS API", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Decision engine interrupted", e);
    }
  }

  private GeoPosition toIncidentPosition(QGIncidentSituationRead situation) {
    if (situation == null || situation.incident() == null) {
      return null;
    }
    Double latitude = situation.incident().latitude();
    Double longitude = situation.incident().longitude();
    if (latitude == null || longitude == null) {
      return null;
    }
    return new GeoPosition(latitude, longitude);
  }

  private Map<UUID, QGActivePhase> selectPhasesByType(List<QGActivePhase> phases) {
    Map<UUID, QGActivePhase> selected = new HashMap<>();
    if (phases == null) {
      return selected;
    }
    for (QGActivePhase phase : phases) {
      if (phase == null || phase.phaseTypeId() == null) {
        continue;
      }
      QGActivePhase current = selected.get(phase.phaseTypeId());
      if (current == null || priorityValue(phase) > priorityValue(current)) {
        selected.put(phase.phaseTypeId(), phase);
      }
    }
    return selected;
  }

  private Map<UUID, List<VehicleCandidate>> buildCandidatePool(
      QGResourcePlanningRead planning, GeoPosition incidentLocation)
      throws IOException, InterruptedException {
    Set<UUID> requiredVehicleTypes = extractRequiredVehicleTypes(planning);
    List<VehicleAssignmentRead> assignments = dataSource.listActiveAssignments();
    Set<UUID> activeAssignments =
        assignments == null
            ? Set.of()
            : assignments.stream()
                .map(VehicleAssignmentRead::vehicleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

    Map<UUID, InterestPointRead> interestPointCache = new HashMap<>();
    Map<UUID, List<VehicleCandidate>> pool = new HashMap<>();

    for (UUID vehicleTypeId : requiredVehicleTypes) {
      List<VehicleRead> vehicles = dataSource.listVehicles(vehicleTypeId);
      if (vehicles == null) {
        continue;
      }
      List<VehicleCandidate> candidates = new ArrayList<>();
      for (VehicleRead vehicle : vehicles) {
        if (vehicle == null || vehicle.vehicleId() == null) {
          continue;
        }
        if (activeAssignments.contains(vehicle.vehicleId())) {
          continue;
        }
        GeoPosition vehiclePosition = resolveVehiclePosition(vehicle, interestPointCache);
        Double distanceKm = null;
        if (incidentLocation != null && vehiclePosition != null) {
          distanceKm = distanceKm(incidentLocation, vehiclePosition);
        }
        if (!matchesCriteria(vehicle, distanceKm)) {
          continue;
        }
        ScoredCandidate scored = scoringStrategy.score(vehicle, distanceKm);
        candidates.add(
            new VehicleCandidate(
                vehicle, vehiclePosition, distanceKm, scored.score(), scored.rationale()));
      }
      candidates.sort(candidateComparator());
      pool.put(vehicleTypeId, candidates);
    }

    return pool;
  }

  private Set<UUID> extractRequiredVehicleTypes(QGResourcePlanningRead planning) {
    Set<UUID> vehicleTypeIds = new HashSet<>();
    if (planning == null || planning.phaseRequirements() == null) {
      return vehicleTypeIds;
    }
    for (QGPhaseRequirements phase : planning.phaseRequirements()) {
      if (phase.groups() == null) {
        continue;
      }
      for (QGRequirementGroup group : phase.groups()) {
        if (group.requirements() == null) {
          continue;
        }
        group.requirements().stream()
            .map(req -> req.vehicleType() == null ? null : req.vehicleType().vehicleTypeId())
            .filter(Objects::nonNull)
            .forEach(vehicleTypeIds::add);
      }
    }
    return vehicleTypeIds;
  }

  private GeoPosition resolveVehiclePosition(
      VehicleRead vehicle, Map<UUID, InterestPointRead> interestPointCache)
      throws IOException, InterruptedException {
    List<VehiclePositionLogRead> positions =
        dataSource.listVehiclePositionLogs(vehicle.vehicleId(), 1);
    if (positions != null && !positions.isEmpty()) {
      VehiclePositionLogRead latest = positions.get(0);
      if (latest.latitude() != null && latest.longitude() != null) {
        return new GeoPosition(latest.latitude(), latest.longitude());
      }
    }

    if (vehicle.baseInterestPointId() == null) {
      return null;
    }
    InterestPointRead point = interestPointCache.get(vehicle.baseInterestPointId());
    if (point == null) {
      point = dataSource.getInterestPoint(vehicle.baseInterestPointId());
      interestPointCache.put(vehicle.baseInterestPointId(), point);
    }
    if (point == null || point.latitude() == null || point.longitude() == null) {
      return null;
    }
    return new GeoPosition(point.latitude(), point.longitude());
  }

  private boolean matchesCriteria(VehicleRead vehicle, Double distanceKm) {
    if (criteria == null) {
      return true;
    }
    if (criteria.minEnergyLevel() != null) {
      double energyLevel = vehicle.energyLevel() == null ? 0.0 : vehicle.energyLevel();
      if (energyLevel < criteria.minEnergyLevel()) {
        return false;
      }
    }
    if (criteria.maxDistanceKm() != null) {
      if (distanceKm == null) {
        return false;
      }
      if (distanceKm > criteria.maxDistanceKm()) {
        return false;
      }
    }
    return true;
  }

  private Comparator<VehicleCandidate> candidateComparator() {
    return Comparator.comparingDouble(VehicleCandidate::score)
        .reversed()
        .thenComparing(
            candidate ->
                candidate.distanceKm() == null ? Double.MAX_VALUE : candidate.distanceKm());
  }

  private int selectCandidates(
      List<VehicleCandidate> candidates,
      Set<UUID> allocatedVehicles,
      List<VehicleAssignmentProposal> proposals,
      UUID incidentId,
      QGActivePhase phase,
      QGPhaseTypeRef phaseType,
      int count) {
    int selected = 0;
    for (VehicleCandidate candidate : candidates) {
      if (candidate.vehicle() == null || candidate.vehicle().vehicleId() == null) {
        continue;
      }
      if (allocatedVehicles.contains(candidate.vehicle().vehicleId())) {
        continue;
      }
      proposals.add(
          new VehicleAssignmentProposal(
              incidentId,
              phase.incidentPhaseId(),
              phaseType.phaseTypeId(),
              candidate.vehicle().vehicleId(),
              candidate.vehicle().vehicleTypeId(),
              candidate.distanceKm(),
              candidate.vehicle().energyLevel(),
              candidate.score(),
              candidate.rationale()));
      allocatedVehicles.add(candidate.vehicle().vehicleId());
      selected++;
      if (selected >= count) {
        break;
      }
    }
    if (selected < count) {
      log.debug(
          "Missing {} vehicles of type {} for phase {}",
          count - selected,
          phaseType.code(),
          phase.incidentPhaseId());
    }
    return selected;
  }

  private int priorityValue(QGActivePhase phase) {
    return phase.priority() == null ? 0 : phase.priority();
  }

  private int groupPriorityKey(QGRequirementGroup group) {
    return group.priority() == null ? Integer.MAX_VALUE : group.priority();
  }

  private static double distanceKm(GeoPosition from, GeoPosition to) {
    double lat1 = Math.toRadians(from.latitude());
    double lon1 = Math.toRadians(from.longitude());
    double lat2 = Math.toRadians(to.latitude());
    double lon2 = Math.toRadians(to.longitude());

    double dLat = lat2 - lat1;
    double dLon = lon2 - lon1;

    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return 6371.0 * c;
  }

  private record GeoPosition(double latitude, double longitude) {}

  private record VehicleCandidate(
      VehicleRead vehicle,
      GeoPosition position,
      Double distanceKm,
      double score,
      String rationale) {}

  static final class RequirementAggregator {

    public Map<UUID, Integer> aggregateGroup(QGRequirementGroup group) {
      Map<UUID, Integer> requiredByType = new HashMap<>();
      if (group == null || group.requirements() == null || group.requirements().isEmpty()) {
        return requiredByType;
      }

      int groupTotal = 0;
      for (QGRequirement requirement : group.requirements()) {
        int count = requirement.minQuantity() == null ? 0 : requirement.minQuantity();
        groupTotal += count;
        if (count > 0 && requirement.vehicleType() != null) {
          requiredByType.merge(requirement.vehicleType().vehicleTypeId(), count, Integer::sum);
        }
      }

      int targetTotal = groupTotal;
      if (group.minTotal() != null) {
        targetTotal = Math.max(targetTotal, group.minTotal());
      }
      if (group.maxTotal() != null) {
        targetTotal = Math.min(targetTotal, group.maxTotal());
      }

      if (targetTotal > groupTotal) {
        List<QGRequirement> sorted = new ArrayList<>(group.requirements());
        sorted.sort(Comparator.comparing(this::preferenceRankKey));
        int remaining = targetTotal - groupTotal;
        int index = 0;
        while (remaining > 0) {
          QGRequirement requirement = sorted.get(index % sorted.size());
          if (requirement.vehicleType() != null) {
            requiredByType.merge(requirement.vehicleType().vehicleTypeId(), 1, Integer::sum);
          }
          remaining--;
          index++;
        }
      }

      return requiredByType;
    }

    private int preferenceRankKey(QGRequirement requirement) {
      Integer preference = requirement.preferenceRank();
      return preference == null ? Integer.MAX_VALUE : preference;
    }
  }
}
