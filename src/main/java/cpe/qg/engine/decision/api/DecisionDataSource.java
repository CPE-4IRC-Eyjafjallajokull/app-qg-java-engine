package cpe.qg.engine.decision.api;

import cpe.qg.engine.decision.model.GeoPoint;
import cpe.qg.engine.decision.model.TravelEstimate;
import cpe.qg.engine.sdmis.dto.QGIncidentSituationRead;
import cpe.qg.engine.sdmis.dto.QGResourcePlanningRead;
import cpe.qg.engine.sdmis.dto.QGVehicleRead;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Abstraction over external data required for decision making. */
public interface DecisionDataSource {

  QGIncidentSituationRead getIncidentSituation(UUID incidentId)
      throws IOException, InterruptedException;

  QGResourcePlanningRead getResourcePlanning(UUID incidentId)
      throws IOException, InterruptedException;

  List<QGVehicleRead> listVehicles() throws IOException, InterruptedException;

  TravelEstimate estimateTravel(GeoPoint from, GeoPoint to)
      throws IOException, InterruptedException;
}
