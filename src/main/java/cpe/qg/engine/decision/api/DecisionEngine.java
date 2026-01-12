package cpe.qg.engine.decision.api;

import cpe.qg.engine.decision.model.AssignmentRequest;
import cpe.qg.engine.decision.model.DecisionResult;

/** Produces vehicle assignment proposals for an incident. */
public interface DecisionEngine {

  DecisionResult proposeAssignments(AssignmentRequest request);
}
