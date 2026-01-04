package cpe.qg.engine.decision.api;

/** Score and explanatory rationale for a candidate. */
public record ScoredCandidate(double score, String rationale) {}
