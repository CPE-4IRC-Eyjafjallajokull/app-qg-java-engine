package cpe.qg.engine.decision.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import cpe.qg.engine.decision.api.DecisionDataSource;
import cpe.qg.engine.sdmis.SdmisApiClient;
import cpe.qg.engine.sdmis.dto.InterestPointRead;
import cpe.qg.engine.sdmis.dto.QGIncidentSituationRead;
import cpe.qg.engine.sdmis.dto.QGResourcePlanningRead;
import cpe.qg.engine.sdmis.dto.VehicleAssignmentRead;
import cpe.qg.engine.sdmis.dto.VehiclePositionLogRead;
import cpe.qg.engine.sdmis.dto.VehicleRead;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** SDMIS API-backed data source for decision making. */
public final class SdmisDecisionDataSource implements DecisionDataSource {

  private static final TypeReference<List<VehicleRead>> VEHICLE_LIST = new TypeReference<>() {};
  private static final TypeReference<List<VehicleAssignmentRead>> ASSIGNMENT_LIST =
      new TypeReference<>() {};
  private static final TypeReference<List<VehiclePositionLogRead>> POSITION_LIST =
      new TypeReference<>() {};

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
  public QGResourcePlanningRead getResourcePlanning(UUID incidentId)
      throws IOException, InterruptedException {
    return client.getJson(
        "/qg/incidents/%s/planification-ressources".formatted(incidentId),
        QGResourcePlanningRead.class);
  }

  @Override
  public List<VehicleRead> listVehicles(UUID vehicleTypeId)
      throws IOException, InterruptedException {
    return client.getJsonList(
        "/vehicles?vehicle_type_id=%s".formatted(vehicleTypeId), VEHICLE_LIST);
  }

  @Override
  public List<VehicleAssignmentRead> listActiveAssignments()
      throws IOException, InterruptedException {
    return client.getJsonList("/vehicles/assignments?active_only=true", ASSIGNMENT_LIST);
  }

  @Override
  public List<VehiclePositionLogRead> listVehiclePositionLogs(UUID vehicleId, int limit)
      throws IOException, InterruptedException {
    return client.getJsonList(
        "/vehicles/position-logs?vehicle_id=%s&limit=%d".formatted(vehicleId, limit),
        POSITION_LIST);
  }

  @Override
  public InterestPointRead getInterestPoint(UUID interestPointId)
      throws IOException, InterruptedException {
    return client.getJson(
        "/interest-points/%s".formatted(interestPointId), InterestPointRead.class);
  }
}
