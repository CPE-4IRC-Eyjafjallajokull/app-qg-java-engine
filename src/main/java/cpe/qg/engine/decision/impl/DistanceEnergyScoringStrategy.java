package cpe.qg.engine.decision.impl;

import cpe.qg.engine.decision.api.ScoredCandidate;
import cpe.qg.engine.decision.api.VehicleScoringStrategy;
import cpe.qg.engine.sdmis.dto.VehicleRead;

/** Basic scoring based on distance to the incident and vehicle energy level. */
public final class DistanceEnergyScoringStrategy implements VehicleScoringStrategy {

  private static final double DISTANCE_WEIGHT = 0.7;
  private static final double ENERGY_WEIGHT = 0.3;

  @Override
  public ScoredCandidate score(VehicleRead vehicle, Double distanceKm) {
    double distanceScore = 0.0;
    if (distanceKm != null) {
      distanceScore = 1.0 / (1.0 + distanceKm);
    }
    double energyScore = vehicle.energyLevel() == null ? 0.0 : clamp(vehicle.energyLevel());
    double score = (distanceScore * DISTANCE_WEIGHT) + (energyScore * ENERGY_WEIGHT);
    String rationale =
        "distance_km=%s, energy_level=%s".formatted(distanceKm, vehicle.energyLevel());
    return new ScoredCandidate(score, rationale);
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
