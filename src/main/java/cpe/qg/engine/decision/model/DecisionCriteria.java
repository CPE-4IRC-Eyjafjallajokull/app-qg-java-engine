package cpe.qg.engine.decision.model;

/** Optional filters that influence vehicle selection. */
public record DecisionCriteria(Double maxDistanceKm, Double minEnergyLevel) {}
