package cpe.qg.engine.decision.api;

import cpe.qg.engine.sdmis.dto.VehicleRead;

/** Computes a score and rationale for a vehicle candidate. */
public interface VehicleScoringStrategy {

  ScoredCandidate score(VehicleRead vehicle, Double distanceKm);
}
