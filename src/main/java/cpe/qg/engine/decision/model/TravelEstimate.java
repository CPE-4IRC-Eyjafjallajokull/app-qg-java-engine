package cpe.qg.engine.decision.model;

/** Distance, travel time and geometry estimation for a route. */
public record TravelEstimate(
    Double distanceKm, Double durationMinutes, RouteGeometry routeGeometry) {}
