package cpe.qg.engine.sdmis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGIncidentSituationRead(
    QGIncidentSnapshot incident, List<QGActivePhase> phasesActive) {}
