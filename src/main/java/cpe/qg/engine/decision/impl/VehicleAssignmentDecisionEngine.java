package cpe.qg.engine.decision.impl;

import cpe.qg.engine.decision.api.DecisionDataSource;
import cpe.qg.engine.decision.api.DecisionEngine;
import cpe.qg.engine.decision.api.ScoredCandidate;
import cpe.qg.engine.decision.api.VehicleScoringStrategy;
import cpe.qg.engine.decision.model.AssignmentRequest;
import cpe.qg.engine.decision.model.DecisionCriteria;
import cpe.qg.engine.decision.model.DecisionResult;
import cpe.qg.engine.decision.model.GeoPoint;
import cpe.qg.engine.decision.model.MissingVehicle;
import cpe.qg.engine.decision.model.RouteGeometry;
import cpe.qg.engine.decision.model.TravelEstimate;
import cpe.qg.engine.decision.model.VehicleAssignmentProposal;
import cpe.qg.engine.decision.model.VehicleNeed;
import cpe.qg.engine.logging.LoggerProvider;
import cpe.qg.engine.sdmis.dto.QGIncidentSituationRead;
import cpe.qg.engine.sdmis.dto.QGVehicleRead;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;

/** Decision engine that proposes vehicles for requested incident phases. */
public final class VehicleAssignmentDecisionEngine implements DecisionEngine {

  private final DecisionDataSource dataSource;
  private final VehicleScoringStrategy scoringStrategy;
  private final DecisionCriteria criteria;
  private final Logger log = LoggerProvider.getLogger(VehicleAssignmentDecisionEngine.class);

  public VehicleAssignmentDecisionEngine(
      DecisionDataSource dataSource,
      VehicleScoringStrategy scoringStrategy,
      DecisionCriteria criteria) {
    this.dataSource = Objects.requireNonNull(dataSource, "Decision data source is required");
    this.scoringStrategy = Objects.requireNonNull(scoringStrategy, "Scoring strategy is required");
    this.criteria = criteria;
  }

  @Override
  public DecisionResult proposeAssignments(AssignmentRequest request) {
    Objects.requireNonNull(request, "Assignment request is required");
    Objects.requireNonNull(request.incidentId(), "Incident id is required");

    Map<UUID, Map<UUID, Integer>> requiredByPhase = aggregateNeedsByPhase(request.vehiclesNeeded());
    if (requiredByPhase.isEmpty()) {
      return new DecisionResult(List.of(), List.of());
    }

    try {
      QGIncidentSituationRead situation = dataSource.getIncidentSituation(request.incidentId());
      GeoPoint incidentLocation = toIncidentPosition(situation);
      List<QGVehicleRead> vehicles = dataSource.listVehicles();
      Set<UUID> requiredVehicleTypes = extractRequiredVehicleTypes(requiredByPhase);
      Map<UUID, List<VehicleCandidate>> candidatesByType =
          buildCandidatesByType(vehicles, requiredVehicleTypes, incidentLocation);

      Set<UUID> allocatedVehicles = new HashSet<>();
      List<MissingVehicle> missing = new ArrayList<>();
      List<VehicleAssignmentProposal> proposals = new ArrayList<>();

      for (Map.Entry<UUID, Map<UUID, Integer>> phaseEntry : requiredByPhase.entrySet()) {
        UUID incidentPhaseId = phaseEntry.getKey();
        Map<UUID, Integer> requiredByType = phaseEntry.getValue();
        List<VehicleCandidate> selected = new ArrayList<>();

        for (Map.Entry<UUID, Integer> requirement : requiredByType.entrySet()) {
          UUID vehicleTypeId = requirement.getKey();
          int needed = requirement.getValue();
          if (needed <= 0) {
            continue;
          }
          List<VehicleCandidate> candidates =
              candidatesByType.getOrDefault(vehicleTypeId, List.of());
          int selectedCount = selectCandidates(candidates, allocatedVehicles, selected, needed);
          if (selectedCount < needed) {
            missing.add(new MissingVehicle(incidentPhaseId, vehicleTypeId, needed - selectedCount));
          }
        }

        selected.sort(candidateComparator());
        int rank = 1;
        for (VehicleCandidate candidate : selected) {
          if (candidate.vehicle() == null || candidate.vehicle().vehicleId() == null) {
            continue;
          }
          proposals.add(
              new VehicleAssignmentProposal(
                  incidentPhaseId,
                  candidate.vehicle().vehicleId(),
                  candidate.distanceKm(),
                  candidate.estimatedTimeMin(),
                  candidate.routeGeometry(),
                  candidate.vehicle().energyLevel(),
                  candidate.score(),
                  rank++));
        }
      }

      return new DecisionResult(proposals, missing);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch decision data from SDMIS API", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Decision engine interrupted", e);
    }
  }

  private Map<UUID, Map<UUID, Integer>> aggregateNeedsByPhase(List<VehicleNeed> needs) {
    Map<UUID, Map<UUID, Integer>> requiredByPhase = new LinkedHashMap<>();
    if (needs == null || needs.isEmpty()) {
      return requiredByPhase;
    }
    for (VehicleNeed need : needs) {
      if (need == null || need.incidentPhaseId() == null || need.vehicleTypeId() == null) {
        continue;
      }
      if (need.quantity() <= 0) {
        continue;
      }
      requiredByPhase
          .computeIfAbsent(need.incidentPhaseId(), ignored -> new LinkedHashMap<>())
          .merge(need.vehicleTypeId(), need.quantity(), Integer::sum);
    }
    return requiredByPhase;
  }

  private Set<UUID> extractRequiredVehicleTypes(Map<UUID, Map<UUID, Integer>> requiredByPhase) {
    Set<UUID> requiredTypes = new HashSet<>();
    for (Map<UUID, Integer> byType : requiredByPhase.values()) {
      requiredTypes.addAll(byType.keySet());
    }
    return requiredTypes;
  }

  private GeoPoint toIncidentPosition(QGIncidentSituationRead situation) {
    if (situation == null || situation.incident() == null) {
      return null;
    }
    GeoPoint point =
        new GeoPoint(situation.incident().latitude(), situation.incident().longitude());
    return point.isDefined() ? point : null;
  }

  private Map<UUID, List<VehicleCandidate>> buildCandidatesByType(
      List<QGVehicleRead> vehicles, Set<UUID> requiredVehicleTypes, GeoPoint incidentLocation) {
    Map<UUID, List<VehicleCandidate>> pool = new HashMap<>();
    if (vehicles == null || vehicles.isEmpty() || requiredVehicleTypes.isEmpty()) {
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
                  scored.score()));
    }

    for (List<VehicleCandidate> candidates : pool.values()) {
      candidates.sort(candidateComparator());
    }

    return pool;
  }

  private int selectCandidates(
      List<VehicleCandidate> candidates,
      Set<UUID> allocatedVehicles,
      List<VehicleCandidate> selected,
      int needed) {
    if (needed <= 0 || candidates == null || candidates.isEmpty()) {
      return 0;
    }
    int selectedCount = 0;
    for (VehicleCandidate candidate : candidates) {
      if (candidate.vehicle() == null || candidate.vehicle().vehicleId() == null) {
        continue;
      }
      UUID vehicleId = candidate.vehicle().vehicleId();
      if (allocatedVehicles.contains(vehicleId)) {
        continue;
      }
      selected.add(candidate);
      allocatedVehicles.add(vehicleId);
      selectedCount++;
      if (selectedCount >= needed) {
        break;
      }
    }
    return selectedCount;
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
      double score) {}
}
