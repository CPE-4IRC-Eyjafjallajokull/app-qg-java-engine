package cpe.qg.engine.decision.api;

import cpe.qg.engine.sdmis.dto.InterestPointRead;
import cpe.qg.engine.sdmis.dto.QGIncidentSituationRead;
import cpe.qg.engine.sdmis.dto.QGResourcePlanningRead;
import cpe.qg.engine.sdmis.dto.VehicleAssignmentRead;
import cpe.qg.engine.sdmis.dto.VehiclePositionLogRead;
import cpe.qg.engine.sdmis.dto.VehicleRead;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Abstraction over external data required for decision making. */
public interface DecisionDataSource {

  QGIncidentSituationRead getIncidentSituation(UUID incidentId)
      throws IOException, InterruptedException;

  QGResourcePlanningRead getResourcePlanning(UUID incidentId)
      throws IOException, InterruptedException;

  List<VehicleRead> listVehicles(UUID vehicleTypeId) throws IOException, InterruptedException;

  List<VehicleAssignmentRead> listActiveAssignments() throws IOException, InterruptedException;

  List<VehiclePositionLogRead> listVehiclePositionLogs(UUID vehicleId, int limit)
      throws IOException, InterruptedException;

  InterestPointRead getInterestPoint(UUID interestPointId) throws IOException, InterruptedException;
}
