package cpe.qg.engine.decision.model;

/** Simple geographic point with latitude and longitude. */
public record GeoPoint(Double latitude, Double longitude) {

  public boolean isDefined() {
    return latitude != null && longitude != null;
  }
}
