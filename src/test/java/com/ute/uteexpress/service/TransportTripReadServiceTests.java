package com.ute.uteexpress.service;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static com.ute.uteexpress.transport.TransportFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransportTripReadServiceTests {
    final LogisticsAccessService access = mock(LogisticsAccessService.class);
    final TransportRouteRepository routes = mock(TransportRouteRepository.class);
    final TransportTripRepository trips = mock(TransportTripRepository.class);
    final TransportTripItemRepository items = mock(TransportTripItemRepository.class);
    final TripReceiptRepository receipts = mock(TripReceiptRepository.class);
    final TripDiscrepancyRepository discrepancies = mock(TripDiscrepancyRepository.class);
    final TransportTripEventRepository events = mock(TransportTripEventRepository.class);
    final TransportTripReadService read = new TransportTripReadService(access, routes, trips, items, receipts, discrepancies, events);
    @Test void scopedTripLookupHappensBeforeAnyChildOrEvidenceRead() {
        User actor = actor(point(11, BranchType.LOCAL_BRANCH)); when(access.requireManager()).thenReturn(actor);
        when(trips.findManagedById(999L, actor.getId())).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> read.detail(999L, 0, 0, 0, 0)); verifyNoInteractions(items, receipts, discrepancies, events);
    }
    @Test void listsAndRouteOptionsUseActualActorAndBoundedDatabasePaging() {
        User actor = actor(point(11, BranchType.LOCAL_BRANCH)); when(access.requireManager()).thenReturn(actor);
        when(trips.findManaged(eq(actor.getId()), any())).thenReturn(Page.empty()); when(routes.findAvailable(eq(actor.getId()), any())).thenReturn(Page.empty());
        read.list(3); read.routes(2);
        verify(trips).findManaged(eq(actor.getId()), argThat(p -> p.getPageNumber() == 3 && p.getPageSize() == 20));
        verify(routes).findAvailable(eq(actor.getId()), argThat(p -> p.getPageNumber() == 2 && p.getPageSize() == 20));
    }
    @Test void historyAndLocalReadyAreScopedInRepositoryRatherThanFilteringLoadedParcels() {
        User actor = actor(point(11, BranchType.LOCAL_BRANCH)); when(access.requireManager()).thenReturn(actor);
        when(items.findManagedHistory(eq(31L), eq(actor.getId()), any())).thenReturn(Page.empty());
        when(items.findLocalReady(eq(actor.getId()), any())).thenReturn(Page.empty());
        assertTrue(read.history(31L, 1).isEmpty()); assertTrue(read.localReady(0).isEmpty());
        verify(items).findManagedHistory(eq(31L), eq(actor.getId()), argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 20));
        verify(items).findLocalReady(eq(actor.getId()), argThat(p -> p.getPageSize() == 20)); verifyNoInteractions(receipts);
    }
}
