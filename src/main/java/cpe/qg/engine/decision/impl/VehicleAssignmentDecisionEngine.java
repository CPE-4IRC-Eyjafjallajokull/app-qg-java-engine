package cpe.qg.engine.decision.impl;

import cpe.qg.engine.decision.api.DecisionDataSource;
import cpe.qg.engine.decision.api.DecisionEngine;
import cpe.qg.engine.decision.api.ScoredCandidate;
import cpe.qg.engine.decision.api.VehicleScoringStrategy;
import cpe.qg.engine.decision.model.DecisionCriteria;
import cpe.qg.engine.decision.model.DecisionResult;
import cpe.qg.engine.decision.model.GeoPoint;
import cpe.qg.engine.decision.model.RouteGeometry;
import cpe.qg.engine.decision.model.TravelEstimate;
import cpe.qg.engine.decision.model.VehicleAssignmentProposal;
import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.sdmis.dto.QGActivePhase;
import cpe.qg.engine.sdmis.dto.QGIncidentSituationRead;
import cpe.qg.engine.sdmis.dto.QGPhaseRequirements;
import cpe.qg.engine.sdmis.dto.QGPhaseTypeRef;
import cpe.qg.engine.sdmis.dto.QGRequirement;
import cpe.qg.engine.sdmis.dto.QGRequirementGroup;
import cpe.qg.engine.sdmis.dto.QGResourcePlanningRead;
import cpe.qg.engine.sdmis.dto.QGVehicleRead;
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

      GeoPoint incidentLocation = toIncidentPosition(situation);
      Map<UUID, QGActivePhase> phasesByType = selectPhasesByType(situation.phasesActive());
      Map<UUID, Integer> remainingByType = new HashMap<>(extractAvailableVehicleTypes(planning));
      List<QGVehicleRead> vehicles = dataSource.listVehicles();
      Set<UUID> requiredVehicleTypes = extractRequiredVehicleTypes(planning);
      Map<UUID, List<VehicleCandidate>> candidatesByType =
          buildCandidatesByType(vehicles, requiredVehicleTypes, incidentLocation);

      Set<UUID> allocatedVehicles = new HashSet<>();
      Map<UUID, Integer> missingByType = new HashMap<>();
      List<VehicleAssignmentProposal> proposals = new ArrayList<>();

      List<QGPhaseRequirements> phaseRequirements =
          planning.phaseRequirements() == null ? List.of() : planning.phaseRequirements();

      for (QGPhaseRequirements phase : phaseRequirements) {
        if (phase.phaseType() == null || phase.phaseType().phaseTypeId() == null) {
          continue;
        }
        List<QGRequirementGroup> groups = phase.groups() == null ? List.of() : phase.groups();
        if (groups.isEmpty()) {
          log.warn(
              "Phase type {} has no assignment groups configured",
              phase.phaseType().code() == null
                  ? phase.phaseType().phaseTypeId()
                  : phase.phaseType().code());
          continue;
        }
        QGActivePhase selectedPhase = phasesByType.get(phase.phaseType().phaseTypeId());
        if (selectedPhase == null || selectedPhase.incidentPhaseId() == null) {
          continue;
        }
        Map<UUID, List<VehicleCandidate>> phasePool =
            buildPhaseCandidatePool(groups, candidatesByType, allocatedVehicles, remainingByType);
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
            List<VehicleCandidate> candidates = phasePool.getOrDefault(vehicleTypeId, List.of());
            int selected =
                selectCandidates(
                    candidates,
                    allocatedVehicles,
                    remainingByType,
                    proposals,
                    selectedPhase,
                    phase.phaseType(),
                    vehicleTypeId,
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

  private GeoPoint toIncidentPosition(QGIncidentSituationRead situation) {
    if (situation == null || situation.incident() == null) {
      return null;
    }
    GeoPoint point =
        new GeoPoint(situation.incident().latitude(), situation.incident().longitude());
    return point.isDefined() ? point : null;
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

  private Map<UUID, List<VehicleCandidate>> buildCandidatesByType(
      List<QGVehicleRead> vehicles, Set<UUID> requiredVehicleTypes, GeoPoint incidentLocation) {
    Map<UUID, List<VehicleCandidate>> pool = new HashMap<>();
    if (vehicles == null || vehicles.isEmpty()) {
      return pool;
    }

    for (QGVehicleRead vehicle : vehicles) {
      if (vehicle == null || vehicle.vehicleId() == null || vehicle.vehicleType() == null) {
        continue;
      }
      UUID vehicleTypeId = vehicle.vehicleType().vehicleTypeId();
      if (vehicleTypeId == null || !requiredVehicleTypes.contains(vehicleTypeId)) {
        continue;
      }
      if (vehicle.activeAssignment() != null) {
        continue;
      }
      GeoPoint vehiclePosition = resolveVehiclePosition(vehicle);
      Double distanceKm = null;
      Double estimatedTimeMin = null;
      RouteGeometry routeGeometry = null;
      if (incidentLocation != null && vehiclePosition != null) {
        TravelEstimate travelEstimate =
            resolveTravelEstimate(vehicle, vehiclePosition, incidentLocation);
        if (travelEstimate != null) {
          if (travelEstimate.distanceKm() != null) {
            distanceKm = travelEstimate.distanceKm();
          }
          estimatedTimeMin = travelEstimate.durationMinutes();
          routeGeometry = travelEstimate.routeGeometry();
        }
        if (distanceKm == null && incidentLocation.isDefined() && vehiclePosition.isDefined()) {
          distanceKm = distanceKm(incidentLocation, vehiclePosition);
        }
      }
      if (!matchesCriteria(vehicle, distanceKm)) {
        continue;
      }
      ScoredCandidate scored = scoringStrategy.score(vehicle, distanceKm, estimatedTimeMin);
      pool.computeIfAbsent(vehicleTypeId, ignored -> new ArrayList<>())
          .add(
              new VehicleCandidate(
                  vehicle,
                  vehiclePosition,
                  distanceKm,
                  estimatedTimeMin,
                  routeGeometry,
                  scored.score(),
                  scored.rationale()));
    }

    for (List<VehicleCandidate> candidates : pool.values()) {
      candidates.sort(candidateComparator());
    }

    return pool;
  }

  private Map<UUID, List<VehicleCandidate>> buildPhaseCandidatePool(
      List<QGRequirementGroup> groups,
      Map<UUID, List<VehicleCandidate>> candidatesByType,
      Set<UUID> allocatedVehicles,
      Map<UUID, Integer> remainingByType) {
    Map<UUID, List<VehicleCandidate>> phasePool = new HashMap<>();
    Set<UUID> phaseVehicleTypes = new HashSet<>();
    for (QGRequirementGroup group : groups) {
      if (group.requirements() == null) {
        continue;
      }
      group.requirements().stream()
          .map(req -> req.vehicleType() == null ? null : req.vehicleType().vehicleTypeId())
          .filter(Objects::nonNull)
          .forEach(phaseVehicleTypes::add);
    }
    for (UUID vehicleTypeId : phaseVehicleTypes) {
      int remaining = remainingByType.getOrDefault(vehicleTypeId, 0);
      if (remaining <= 0) {
        continue;
      }
      List<VehicleCandidate> candidates = candidatesByType.getOrDefault(vehicleTypeId, List.of());
      if (candidates.isEmpty()) {
        continue;
      }
      List<VehicleCandidate> filtered = new ArrayList<>();
      for (VehicleCandidate candidate : candidates) {
        if (candidate.vehicle() == null || candidate.vehicle().vehicleId() == null) {
          continue;
        }
        if (allocatedVehicles.contains(candidate.vehicle().vehicleId())) {
          continue;
        }
        filtered.add(candidate);
        if (filtered.size() >= remaining) {
          break;
        }
      }
      if (!filtered.isEmpty()) {
        phasePool.put(vehicleTypeId, filtered);
      }
    }
    return phasePool;
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

  private Map<UUID, Integer> extractAvailableVehicleTypes(QGResourcePlanningRead planning) {
    Map<UUID, Integer> availableByType = new HashMap<>();
    if (planning == null || planning.availability() == null) {
      return availableByType;
    }
    for (var availability : planning.availability()) {
      if (availability == null || availability.vehicleType() == null) {
        continue;
      }
      UUID vehicleTypeId = availability.vehicleType().vehicleTypeId();
      if (vehicleTypeId == null) {
        continue;
      }
      int available = availability.available() == null ? 0 : availability.available();
      availableByType.merge(vehicleTypeId, available, Integer::sum);
    }
    return availableByType;
  }

  private GeoPoint resolveVehiclePosition(QGVehicleRead vehicle) {
    if (vehicle.currentPosition() != null
        && vehicle.currentPosition().latitude() != null
        && vehicle.currentPosition().longitude() != null) {
      return new GeoPoint(
          vehicle.currentPosition().latitude(), vehicle.currentPosition().longitude());
    }
    if (vehicle.baseInterestPoint() != null
        && vehicle.baseInterestPoint().latitude() != null
        && vehicle.baseInterestPoint().longitude() != null) {
      return new GeoPoint(
          vehicle.baseInterestPoint().latitude(), vehicle.baseInterestPoint().longitude());
    }
    return null;
  }

  private TravelEstimate resolveTravelEstimate(
      QGVehicleRead vehicle, GeoPoint vehiclePosition, GeoPoint incidentLocation) {
    if (vehiclePosition == null
        || incidentLocation == null
        || !vehiclePosition.isDefined()
        || !incidentLocation.isDefined()) {
      return null;
    }
    try {
      return dataSource.estimateTravel(vehiclePosition, incidentLocation);
    } catch (IllegalStateException e) {
      log.warn("Routing API call failed for vehicle {}: {}", vehicle.vehicleId(), e.getMessage());
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Travel estimation interrupted", e);
    } catch (IOException e) {
      log.warn(
          "Failed to fetch route estimate for vehicle {} ({}). Using fallback distance.",
          vehicle.vehicleId(),
          e.getMessage());
      return null;
    }
  }

  private boolean matchesCriteria(QGVehicleRead vehicle, Double distanceKm) {
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
                candidate.estimatedTimeMin() == null
                    ? Double.MAX_VALUE
                    : candidate.estimatedTimeMin())
        .thenComparing(
            candidate ->
                candidate.distanceKm() == null ? Double.MAX_VALUE : candidate.distanceKm());
  }

  private int selectCandidates(
      List<VehicleCandidate> candidates,
      Set<UUID> allocatedVehicles,
      Map<UUID, Integer> remainingByType,
      List<VehicleAssignmentProposal> proposals,
      QGActivePhase phase,
      QGPhaseTypeRef phaseType,
      UUID vehicleTypeId,
      int count) {
    int selected = 0;
    int remaining = remainingByType.getOrDefault(vehicleTypeId, 0);
    if (remaining <= 0) {
      return selected;
    }
    for (VehicleCandidate candidate : candidates) {
      if (candidate.vehicle() == null || candidate.vehicle().vehicleId() == null) {
        continue;
      }
      if (allocatedVehicles.contains(candidate.vehicle().vehicleId())) {
        continue;
      }
      proposals.add(
          new VehicleAssignmentProposal(
              phase.incidentPhaseId(),
              candidate.vehicle().vehicleId(),
              candidate.distanceKm(),
              candidate.estimatedTimeMin(),
              candidate.routeGeometry(),
              candidate.vehicle().energyLevel(),
              candidate.score(),
              candidate.rationale()));
      allocatedVehicles.add(candidate.vehicle().vehicleId());
      selected++;
      remaining--;
      if (selected >= count) {
        break;
      }
      if (remaining <= 0) {
        break;
      }
    }
    remainingByType.put(vehicleTypeId, remaining);
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

  private static double distanceKm(GeoPoint from, GeoPoint to) {
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

  private record VehicleCandidate(
      QGVehicleRead vehicle,
      GeoPoint position,
      Double distanceKm,
      Double estimatedTimeMin,
      RouteGeometry routeGeometry,
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
