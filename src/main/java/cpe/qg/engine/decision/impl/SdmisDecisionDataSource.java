package cpe.qg.engine.decision.impl;

import cpe.qg.engine.decision.api.DecisionDataSource;
import cpe.qg.engine.decision.model.GeoPoint;
import cpe.qg.engine.decision.model.RouteGeometry;
import cpe.qg.engine.decision.model.TravelEstimate;
import cpe.qg.engine.sdmis.SdmisApiClient;
import cpe.qg.engine.sdmis.dto.QGIncidentSituationRead;
import cpe.qg.engine.sdmis.dto.QGRoutePoint;
import cpe.qg.engine.sdmis.dto.QGRouteRequest;
import cpe.qg.engine.sdmis.dto.QGRouteResponse;
import cpe.qg.engine.sdmis.dto.QGVehicleRead;
import cpe.qg.engine.sdmis.dto.QGVehiclesListRead;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** SDMIS API-backed data source for decision making. */
public final class SdmisDecisionDataSource implements DecisionDataSource {

  private final SdmisApiClient client;

  public SdmisDecisionDataSource(SdmisApiClient client) {
    this.client = Objects.requireNonNull(client, "SDMIS API client is required");
  }

  @Override
  public QGIncidentSituationRead getIncidentSituation(UUID incidentId)
      throws IOException, InterruptedException {
    return client.getJson(
        "/qg/incidents/%s/situation".formatted(incidentId), QGIncidentSituationRead.class);
  }

  @Override
  public List<QGVehicleRead> listVehicles() throws IOException, InterruptedException {
    QGVehiclesListRead response = client.getJson("/qg/vehicles", QGVehiclesListRead.class);
    if (response == null || response.vehicles() == null) {
      return List.of();
    }
    return response.vehicles();
  }

  @Override
  public TravelEstimate estimateTravel(GeoPoint from, GeoPoint to)
      throws IOException, InterruptedException {
    if (from == null || to == null || !from.isDefined() || !to.isDefined()) {
      return null;
    }
    QGRouteRequest request =
        new QGRouteRequest(
            new QGRoutePoint(from.latitude(), from.longitude()),
            new QGRoutePoint(to.latitude(), to.longitude()),
            false);
    QGRouteResponse response = client.postJson("/geo/route", request, QGRouteResponse.class);
    if (response == null) {
      return null;
    }
    Double distanceKm = response.distanceM() == null ? null : response.distanceM() / 1000.0;
    Double durationMinutes = response.durationS() == null ? null : response.durationS() / 60.0;
    RouteGeometry geometry =
        response.geometry() == null
            ? null
            : new RouteGeometry(response.geometry().type(), response.geometry().coordinates());
    return new TravelEstimate(distanceKm, durationMinutes, geometry);
  }
}
