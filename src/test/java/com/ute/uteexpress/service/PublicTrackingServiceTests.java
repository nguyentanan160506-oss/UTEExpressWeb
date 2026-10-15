package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.tracking.PublicTrackingView;
import com.ute.uteexpress.enums.ShipmentStatus;
import com.ute.uteexpress.repository.ShipmentEventRepository;
import com.ute.uteexpress.repository.ShipmentRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;

import static com.ute.uteexpress.tracking.TrackingFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicTrackingServiceTests {
    private ShipmentRepository shipments;
    private ShipmentEventRepository events;
    private PublicTrackingService service;

    @BeforeEach void setup() {
        shipments = mock(ShipmentRepository.class); events = mock(ShipmentEventRepository.class);
        service = new PublicTrackingService(shipments, events);
    }

    @Test void validLookupUsesScalarProjectionAndNormalizesCode() {
        when(shipments.findPublicTrackingSummary(CODE)).thenReturn(Optional.of(summary(ShipmentStatus.IN_TRANSIT)));
        when(events.findPublicTrackingEvents(eq(CODE), eq(ShipmentStatus.CREATED),
                eq(PublicTrackingEventPolicy.CREATED_TITLE), any(Pageable.class)))
            .thenReturn(List.of(event(PublicTrackingEventPolicy.CREATED_TITLE, null, ShipmentStatus.CREATED)));
        PublicTrackingView result = service.lookup("  " + CODE.toLowerCase(Locale.ROOT) + "  ").orElseThrow();
        assertEquals(CODE, result.trackingNumber()); assertEquals("Đang vận chuyển", result.statusLabel());
        assertEquals(TIME, result.updatedAt()); assertEquals(1, result.events().size());
        assertEquals("Đã tạo vận đơn", result.events().getFirst().label());
        verify(shipments).findPublicTrackingSummary(CODE);
        verifyNoMoreInteractions(shipments);
    }

    @Test void missingCodeDoesNotQueryEvents() {
        when(shipments.findPublicTrackingSummary(CODE)).thenReturn(Optional.empty());
        assertTrue(service.lookup(CODE).isEmpty()); verifyNoInteractions(events);
    }

    @Test void unpublishableSummaryHasTheSameAbsentResultWithoutTimelineLookup() {
        when(shipments.findPublicTrackingSummary(CODE)).thenReturn(Optional.of(summary(null)));
        assertTrue(service.lookup(CODE).isEmpty()); verifyNoInteractions(events);
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {" ", "31", "UTE123", "UTE0000000000000000000000000000000",
        "UTEZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ", "../user/shipments/31", "' OR 1=1 --",
        "UTEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA_", "<script>alert(1)</script>"})
    void invalidCodesNeverReachTheDatabase(String input) {
        assertThrows(PublicTrackingService.InvalidTrackingNumberException.class, () -> service.lookup(input));
        verifyNoInteractions(shipments, events);
    }

    @Test void oversizedInputsAndNonAsciiLookalikesAreRejectedBeforeQuerying() {
        assertThrows(PublicTrackingService.InvalidTrackingNumberException.class, () -> service.lookup("A".repeat(10000)));
        assertThrows(PublicTrackingService.InvalidTrackingNumberException.class, () -> service.lookup("ＵＴＥ" + "A".repeat(32)));
        assertThrows(PublicTrackingService.InvalidTrackingNumberException.class, () -> service.lookup("UTE" + "ﬀ".repeat(16)));
        verifyNoInteractions(shipments, events);
    }

    @Test void internalAndUnknownEventsAreDeniedEvenWhenTheyShareAPublicStatus() {
        when(shipments.findPublicTrackingSummary(CODE)).thenReturn(Optional.of(summary(ShipmentStatus.CREATED)));
        when(events.findPublicTrackingEvents(anyString(), any(), anyString(), any())).thenReturn(List.of(
            event("INTERNAL_PII_MARKER", null, ShipmentStatus.CREATED),
            event("Đã tạo vận đơn nội bộ", null, ShipmentStatus.CREATED),
            event(PublicTrackingEventPolicy.CREATED_TITLE, ShipmentStatus.CONFIRMED, ShipmentStatus.CREATED),
            event(PublicTrackingEventPolicy.CREATED_TITLE, null, ShipmentStatus.DELIVERED),
            event(PublicTrackingEventPolicy.CREATED_TITLE.toUpperCase(Locale.ROOT), null, ShipmentStatus.CREATED),
            event(PublicTrackingEventPolicy.CREATED_TITLE, null, ShipmentStatus.CREATED)));
        var result = service.lookup(CODE).orElseThrow();
        assertEquals(1, result.events().size());
        assertFalse(result.toString().contains("INTERNAL_PII_MARKER"));
    }

    @Test void noPublicEventDoesNotInventMilestones() {
        when(shipments.findPublicTrackingSummary(CODE)).thenReturn(Optional.of(summary(ShipmentStatus.DELIVERED)));
        when(events.findPublicTrackingEvents(anyString(), any(), anyString(), any())).thenReturn(List.of());
        var result = service.lookup(CODE).orElseThrow();
        assertEquals("Đã giao", result.statusLabel()); assertTrue(result.events().isEmpty());
    }

    @Test void timelineIsBoundedAndQueryGetsOnlyTheExplicitPublicTuple() {
        when(shipments.findPublicTrackingSummary(CODE)).thenReturn(Optional.of(summary(ShipmentStatus.CREATED)));
        var rows = Collections.nCopies(100, event(PublicTrackingEventPolicy.CREATED_TITLE, null, ShipmentStatus.CREATED));
        when(events.findPublicTrackingEvents(anyString(), any(), anyString(), any())).thenReturn(rows);
        assertEquals(20, service.lookup(CODE).orElseThrow().events().size());
        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(events).findPublicTrackingEvents(eq(CODE), eq(ShipmentStatus.CREATED),
            eq(PublicTrackingEventPolicy.CREATED_TITLE), page.capture());
        assertEquals(0, page.getValue().getPageNumber()); assertEquals(20, page.getValue().getPageSize());
    }

    @Test void publicDtoHasOnlyTheApprovedFieldsAndImmutableTimeline() {
        assertEquals(Set.of("trackingNumber", "statusLabel", "updatedAt", "events"),
            componentNames(PublicTrackingView.class));
        assertEquals(Set.of("label", "occurredAt"), componentNames(PublicTrackingView.Event.class));
        var result = new PublicTrackingView(CODE, "Đã tạo", TIME, new ArrayList<>());
        assertThrows(UnsupportedOperationException.class, () -> result.events().add(new PublicTrackingView.Event("x", TIME)));
    }

    private Set<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)
            .collect(java.util.stream.Collectors.toSet());
    }
}
