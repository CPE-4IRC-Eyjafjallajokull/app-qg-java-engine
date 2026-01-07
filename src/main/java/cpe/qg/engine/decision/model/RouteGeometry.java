package cpe.qg.engine.decision.model;

import java.util.List;

/** Route geometry as GeoJSON LineString coordinates. */
public record RouteGeometry(String type, List<List<Double>> coordinates) {}
