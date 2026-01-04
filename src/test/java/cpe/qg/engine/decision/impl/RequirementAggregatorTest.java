package cpe.qg.engine.decision.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cpe.qg.engine.sdmis.dto.QGRequirement;
import cpe.qg.engine.sdmis.dto.QGRequirementGroup;
import cpe.qg.engine.sdmis.dto.QGVehicleTypeRef;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequirementAggregatorTest {

  @Test
  void expandsGroupTotalsUsingPreferenceRanks() {
    UUID typeA = UUID.randomUUID();
    UUID typeB = UUID.randomUUID();

    QGRequirement reqA =
        new QGRequirement(new QGVehicleTypeRef(typeA, "A", null), 1, null, true, 1);
    QGRequirement reqB =
        new QGRequirement(new QGVehicleTypeRef(typeB, "B", null), 0, null, null, 2);
    QGRequirementGroup group =
        new QGRequirementGroup(
            UUID.randomUUID(), "group", "ALL", 3, null, 1, true, List.of(reqA, reqB));

    Map<UUID, Integer> result =
        new VehicleAssignmentDecisionEngine.RequirementAggregator().aggregateGroup(group);

    assertThat(result).containsEntry(typeA, 2).containsEntry(typeB, 1);
  }
}
