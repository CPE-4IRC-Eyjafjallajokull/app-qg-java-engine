package cpe.qg.engine.decision.api;

import cpe.qg.engine.decision.model.DecisionResult;
import java.util.UUID;

/** Produces vehicle assignment proposals for an incident. */
public interface DecisionEngine {

  DecisionResult proposeAssignments(UUID incidentId);
}
