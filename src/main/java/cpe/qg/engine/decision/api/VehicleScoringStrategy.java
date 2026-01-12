package cpe.qg.engine.decision.api;

import cpe.qg.engine.sdmis.dto.QGVehicleRead;

/** Computes a score for a vehicle candidate. */
public interface VehicleScoringStrategy {

  ScoredCandidate score(QGVehicleRead vehicle, Double distanceKm, Double estimatedTimeMin);
}
