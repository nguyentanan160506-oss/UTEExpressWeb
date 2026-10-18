package com.ute.uteexpress.service;

import com.ute.uteexpress.entity.Branch;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ute.uteexpress.shipment.ShipmentFixtures.branch;

class BranchNetworkServiceTests {
    private final BranchServiceAreaRepository areas = mock(BranchServiceAreaRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final BranchNetworkService service = new BranchNetworkService(areas, branches);
    private Branch local, hub;
    @BeforeEach void setup() {
        local = branch(11L); local.setBranchType(BranchType.LOCAL_BRANCH);
        hub = branch(21L); hub.setBranchType(BranchType.CENTRAL_HUB); local.setParentHub(hub);
        when(branches.findByIdAndStatus(11L, BranchStatus.ACTIVE)).thenReturn(Optional.of(local));
    }
    @Test void resolvesOnlyConfiguredActiveHub() { assertEquals(21L, service.configuredHub(11L)); }
    @Test void missingBranchFailsWithoutCreatingData() {
        assertThrows(LogisticsRuleException.class, () -> service.configuredHub(99L));
        verify(branches, never()).save(any());
    }
    @Test void legacyBranchWithoutHubFailsExplicitly() {
        local.setParentHub(null); assertThrows(LogisticsRuleException.class, () -> service.configuredHub(11L));
    }
    @Test void localBranchCannotBeItsOwnHub() {
        local.setParentHub(local); assertThrows(LogisticsRuleException.class, () -> service.configuredHub(11L));
    }
    @Test void hubMustActuallyBeCentralHub() {
        hub.setBranchType(BranchType.LOCAL_BRANCH); assertThrows(LogisticsRuleException.class, () -> service.configuredHub(11L));
    }
    @Test void inactiveHubIsRejected() {
        hub.setStatus(BranchStatus.INACTIVE); assertThrows(LogisticsRuleException.class, () -> service.configuredHub(11L));
    }
    @ParameterizedTest @EnumSource(ShipmentMethod.class)
    void serviceAreasAreScopedByAddressAndMethod(ShipmentMethod method) {
        when(areas.findServingBranches("P", "D", "W", method, BranchStatus.ACTIVE,
            BranchType.LOCAL_BRANCH, BranchType.CENTRAL_HUB)).thenReturn(List.of(local));
        var result = service.servingBranches(" P ", " D ", " W ", method);
        assertEquals(List.of(11L), result.stream().map(option -> option.id()).toList());
        verifyNoInteractions(branches);
    }
    @Test void missingCoverageReturnsEmptyAndDoesNotGuessNearestBranch() {
        assertTrue(service.servingBranches("P", "D", "W", ShipmentMethod.HOME_PICKUP).isEmpty());
        verifyNoInteractions(branches); verify(areas, never()).save(any());
    }
}
