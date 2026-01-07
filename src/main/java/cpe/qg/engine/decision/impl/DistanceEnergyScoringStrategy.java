package cpe.qg.engine.decision.impl;

import cpe.qg.engine.decision.api.ScoredCandidate;
import cpe.qg.engine.decision.api.VehicleScoringStrategy;
import cpe.qg.engine.sdmis.dto.QGVehicleRead;

/** Basic scoring based on distance/time to the incident and vehicle energy level. */
public final class DistanceEnergyScoringStrategy implements VehicleScoringStrategy {

  private static final double DISTANCE_WEIGHT = 0.4;
  private static final double TIME_WEIGHT = 0.4;
  private static final double ENERGY_WEIGHT = 0.2;

  @Override
  public ScoredCandidate score(QGVehicleRead vehicle, Double distanceKm, Double estimatedTimeMin) {
    double distanceScore = invertPositive(distanceKm);
    double timeScore = invertPositive(estimatedTimeMin);
    double energyScore = vehicle.energyLevel() == null ? 0.0 : clamp(vehicle.energyLevel());

    double weightSum = ENERGY_WEIGHT;
    double weightedDistance = 0.0;
    double weightedTime = 0.0;
    if (distanceKm != null) {
      weightSum += DISTANCE_WEIGHT;
      weightedDistance = distanceScore * DISTANCE_WEIGHT;
    }
    if (estimatedTimeMin != null) {
      weightSum += TIME_WEIGHT;
      weightedTime = timeScore * TIME_WEIGHT;
    }

    if (weightSum <= 0.0) {
      return new ScoredCandidate(0.0, "No metrics available for scoring");
    }
    double score = (weightedDistance + weightedTime + (energyScore * ENERGY_WEIGHT)) / weightSum;
    String rationale =
        "distance_km=%s, estimated_time_min=%s, energy_level=%s"
            .formatted(distanceKm, estimatedTimeMin, vehicle.energyLevel());
    return new ScoredCandidate(score, rationale);
  }

  private double invertPositive(Double value) {
    if (value == null) {
      return 0.0;
    }
    double safeValue = value < 0.0 ? 0.0 : value;
    return 1.0 / (1.0 + safeValue);
  }

  private double clamp(Double value) {
    if (value == null) {
      return 0.0;
    }
    if (value < 0.0) {
      return 0.0;
    }
    if (value > 1.0) {
      return 1.0;
    }
    return value;
  }
}
