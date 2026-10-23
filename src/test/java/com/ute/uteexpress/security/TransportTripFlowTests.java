package com.ute.uteexpress.security;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.data.domain.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.ute.uteexpress.transport.TransportFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC, actual guards/lifecycle/read model and Thymeleaf, with isolated repository boundaries. */
@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class TransportTripFlowTests {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean RoleRepository roleCatalog;
    @MockitoBean EmailOtpRepository otps;
    @MockitoBean OtpMailSender mail;
    @MockitoBean ManagerBranchRepository memberships;
    @MockitoBean EmployeeRepository employees;
    @MockitoBean BranchRepository branches;
    @MockitoBean ShipmentRepository shipments;
    @MockitoBean ShipmentAssignmentRepository assignments;
    @MockitoBean ShipmentIntakeRepository intakes;
    @MockitoBean ShipmentEventRepository shipmentEvents;
    @MockitoBean TransportRouteRepository routes;
    @MockitoBean TransportTripRepository trips;
    @MockitoBean TransportTripItemRepository items;
    @MockitoBean TripReceiptRepository receipts;
    @MockitoBean TripDiscrepancyRepository discrepancies;
    @MockitoBean TransportTripEventRepository events;
    Branch origin, hub, destination;
    User sourceActor, hubActor;
    Employee hubEmployee;
    TransportRoute route;
    TransportTrip trip;
    Shipment s;
    TransportTripItem item;
    TripReceipt receipt;
    TripDiscrepancy issue;
    final List<TransportTripEvent> audit = new ArrayList<>();
    final List<ShipmentEvent> timeline = new ArrayList<>();
    @BeforeEach void setup() {
        audit.clear(); timeline.clear(); item = null; receipt = null; issue = null;
        origin = point(11, BranchType.LOCAL_BRANCH); hub = point(101, BranchType.CENTRAL_HUB);
        destination = point(12, BranchType.LOCAL_BRANCH); origin.setParentHub(hub); destination.setParentHub(hub);
        sourceActor = actor(origin); hubActor = actor(hub); hubEmployee = employee(hub);
        for (Branch b : List.of(origin, hub)) {
            User actor = actor(b); when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
            when(roles.findRoleNamesByUserId(actor.getId())).thenReturn(List.of("ROLE_MANAGER"));
            when(branches.findByIdAndStatus(b.getId(), BranchStatus.ACTIVE)).thenReturn(Optional.of(b));
            when(memberships.existsByManagerIdAndBranchId(actor.getId(), b.getId())).thenReturn(true);
            when(employees.findByUserId(actor.getId())).thenReturn(Optional.of(b == hub ? hubEmployee : employee(b)));
        }
        when(items.findReturnCandidates(anyLong(), anyLong(), any(), any())).thenReturn(Page.empty());
        route = route(1, origin, hub, TransportLeg.COLLECTION); when(routes.findById(1L)).thenReturn(Optional.of(route));
        s = parcel(31, origin, destination);
        trip = new TransportTrip(); trip.setId(1L); trip.setRoute(route); trip.setOrigin(origin); trip.setDestination(hub); trip.setLeg(TransportLeg.COLLECTION);
        trip.setCode("TRP" + "A".repeat(32)); trip.setRequestToken("a".repeat(32)); trip.setCreatedBy(sourceActor);
        trip.setCreatedAt(com.ute.uteexpress.shipment.ShipmentFixtures.NOW); trip.setUpdatedAt(trip.getCreatedAt());
        ReflectionTestUtils.setField(trip, "version", 0L);
        when(trips.findForUpdate(1L)).thenAnswer(c -> Optional.of(trip));
        when(trips.findManagedById(eq(1L), anyLong())).thenAnswer(c -> Set.of(sourceActor.getId(), hubActor.getId()).contains(c.getArgument(1)) ? Optional.of(trip) : Optional.empty());
        when(trips.findManaged(anyLong(), any())).thenReturn(new PageImpl<>(List.of(trip)));
        when(routes.findAvailable(eq(sourceActor.getId()), any())).thenReturn(new PageImpl<>(List.of(route)));
        when(trips.saveAndFlush(any())).thenAnswer(c -> {
            TransportTrip t = c.getArgument(0); if (t.getId() == null) { t.setId(2L); }
            ReflectionTestUtils.setField(t, "version", t.getVersion() == null ? 0L : t.getVersion() + 1); return t;
        });
        when(shipments.findForLifecycle(31L)).thenReturn(Optional.of(s)); when(intakes.findByShipmentId(31L)).thenAnswer(c -> Optional.of(intake(s)));
        when(shipments.saveAndFlush(any())).thenAnswer(c -> { ReflectionTestUtils.setField(s, "version", s.getVersion() + 1); return s; });
        when(items.countByTripId(1L)).thenAnswer(c -> item == null ? 0L : 1L);
        when(items.countByTripIdAndStatus(eq(1L), any())).thenAnswer(c -> item != null && item.getStatus() == c.getArgument(1) ? 1L : 0L);
        when(items.findByShipmentIdAndActiveTrue(31L)).thenAnswer(c -> Optional.ofNullable(item).filter(i -> Boolean.TRUE.equals(i.getActive())));
        when(items.findByTripIdAndShipmentId(1L, 31L)).thenAnswer(c -> Optional.ofNullable(item));
        when(items.findByIdAndTripId(101L, 1L)).thenAnswer(c -> Optional.ofNullable(item));
        when(items.findShipmentId(1L, 101L)).thenAnswer(c -> item == null ? Optional.empty() : Optional.of(31L));
        when(items.findShipmentIds(1L)).thenAnswer(c -> item == null ? List.of() : List.of(31L));
        when(items.saveAndFlush(any())).thenAnswer(c -> { item = c.getArgument(0); item.setId(101L); return item; });
        when(items.findByTripId(eq(1L), any())).thenAnswer(c -> item == null ? Page.empty() : new PageImpl<>(List.of(item)));
        when(items.findCandidates(eq(11L), eq(101L), eq(TransportLeg.COLLECTION), any())).thenAnswer(c -> item == null ? new PageImpl<>(List.of(s)) : Page.empty());
        when(items.findLocalReady(anyLong(), any())).thenReturn(Page.empty());
        when(items.findManagedHistory(eq(31L), anyLong(), any())).thenAnswer(c -> item == null ? Page.empty() : new PageImpl<>(List.of(item)));
        when(events.findByTripId(eq(1L), any())).thenAnswer(c -> new PageImpl<>(List.copyOf(audit)));
        when(events.saveAndFlush(any())).thenAnswer(c -> { TransportTripEvent e = c.getArgument(0); audit.add(e); return e; });
        when(shipmentEvents.saveAndFlush(any())).thenAnswer(c -> { ShipmentEvent e = c.getArgument(0); timeline.add(e); return e; });
        when(receipts.existsByItemId(101L)).thenAnswer(c -> receipt != null);
        when(receipts.findByItemId(101L)).thenAnswer(c -> Optional.ofNullable(receipt));
        when(receipts.saveAndFlush(any())).thenAnswer(c -> { receipt = c.getArgument(0); return receipt; });
        when(discrepancies.findByTripId(eq(1L), any())).thenAnswer(c -> issue == null ? Page.empty() : new PageImpl<>(List.of(issue)));
        when(discrepancies.countByTripIdAndStatus(eq(1L), any())).thenAnswer(c -> issue != null && issue.getStatus() == c.getArgument(1) ? 1L : 0L);
        when(discrepancies.findByItemIdAndStatus(eq(101L), any())).thenAnswer(c -> issue != null && issue.getItem() != null && issue.getStatus() == c.getArgument(1) ? List.of(issue) : List.of());
        when(discrepancies.findByIdAndTripId(301L, 1L)).thenAnswer(c -> Optional.ofNullable(issue));
        when(discrepancies.saveAndFlush(any())).thenAnswer(c -> { issue = c.getArgument(0); issue.setId(301L); return issue; });
    }
    MockHttpServletRequestBuilder source(MockHttpServletRequestBuilder r) { return r.with(user(sourceActor.getEmail()).roles("MANAGER")); }
    MockHttpServletRequestBuilder hub(MockHttpServletRequestBuilder r) { return r.with(user(hubActor.getEmail()).roles("MANAGER")); }
    MockHttpServletRequestBuilder action(String suffix, boolean atHub) {
        var r = post("/manager/trips/1/" + suffix).with(csrf()).param("expectedVersion", trip.getVersion().toString())
            .param("reference", "INTERNAL-REFERENCE").param("notes", "ACTUAL-INSPECTION").param("confirmed", "true");
        return atHub ? hub(r) : source(r);
    }
    void add() throws Exception {
        mvc.perform(source(post("/manager/trips/1/items")).with(csrf()).param("expectedVersion", trip.getVersion().toString()).param("shipmentIds", "31"))
            .andExpect(flash().attributeExists("notice"));
    }
    void depart() throws Exception { add(); mvc.perform(action("seal", false)).andExpect(flash().attributeExists("notice")); mvc.perform(action("depart", false)).andExpect(flash().attributeExists("notice")); }
    void arrive() throws Exception { depart(); mvc.perform(action("arrive", true)).andExpect(flash().attributeExists("notice")); }
    @Test void sourcePagesAndManifestRenderActualTemplatesAndCsrf() throws Exception {
        mvc.perform(source(get("/manager/trips"))).andExpect(status().isOk());
        mvc.perform(source(get("/manager/trips/new"))).andExpect(status().isOk());
        mvc.perform(source(get("/manager/trips/1"))).andExpect(status().isOk()).andExpect(r -> {
            String html = r.getResponse().getContentAsString(); assertTrue(html.contains(s.getTrackingNumber())); assertTrue(html.contains("name=\"_csrf\""));
        });
        mvc.perform(source(get("/manager/trips/local-ready"))).andExpect(status().isOk());
        verify(items).findCandidates(eq(11L), eq(101L), eq(TransportLeg.COLLECTION), argThat(p -> p.getPageSize() == 20));
    }
    @Test void actualCollectionArrivalReceiptAndCloseWorkThroughMvc() throws Exception {
        arrive(); assertEquals(ShipmentStatus.IN_TRANSIT, s.getStatus()); assertNull(receipt); assertNull(s.getCurrentBranch());
        mvc.perform(hub(get("/manager/trips/1"))).andExpect(status().isOk());
        mvc.perform(action("receipts", true).param("itemId", "101")).andExpect(flash().attributeExists("notice"));
        assertEquals(ShipmentStatus.AT_DESTINATION_HUB, s.getStatus()); assertSame(hub, s.getCurrentBranch()); assertNotNull(receipt);
        mvc.perform(action("close", true)).andExpect(flash().attributeExists("notice")); assertEquals(TripStatus.CLOSED, trip.getStatus());
        mvc.perform(hub(get("/manager/trips/1"))).andExpect(status().isOk());
        mvc.perform(source(get("/manager/trips/shipments/31/history"))).andExpect(status().isOk());
    }
    @Test void missingAndRecoveryReceiptRenderAndPreserveHistory() throws Exception {
        arrive(); mvc.perform(hub(post("/manager/trips/1/discrepancies")).with(csrf()).param("expectedVersion", trip.getVersion().toString())
            .param("itemId", "101").param("kind", "MISSING").param("reference", "COUNT").param("notes", "Not unloaded"))
            .andExpect(flash().attributeExists("notice")); assertNull(receipt); assertEquals(ShipmentStatus.IN_TRANSIT, s.getStatus());
        mvc.perform(hub(get("/manager/trips/1"))).andExpect(status().isOk());
        mvc.perform(action("receipts", true).param("itemId", "101")).andExpect(flash().attributeExists("error"));
        mvc.perform(action("receipts", true).param("itemId", "101").param("resolveDiscrepancy", "true"))
            .andExpect(flash().attributeExists("notice")); assertEquals(TripDiscrepancyStatus.RESOLVED, issue.getStatus());
    }
    @Test void unexpectedMismatchIsRecordedAndResolvedWithResponsibleConfirmation() throws Exception {
        arrive(); mvc.perform(hub(post("/manager/trips/1/discrepancies")).with(csrf()).param("expectedVersion", trip.getVersion().toString())
            .param("kind", "UNEXPECTED").param("reference", "UNLISTED-OBSERVATION").param("notes", "<script>internal()</script>"))
            .andExpect(flash().attributeExists("notice")); assertNull(issue.getItem()); assertNull(receipt);
        mvc.perform(hub(get("/manager/trips/1"))).andExpect(status().isOk()).andExpect(r -> {
            String html = r.getResponse().getContentAsString(); assertFalse(html.contains("<script>internal()")); assertTrue(html.contains("&lt;script&gt;internal()"));
        });
        mvc.perform(action("discrepancies/resolve", true).param("discrepancyId", "301")).andExpect(flash().attributeExists("notice"));
        assertEquals(TripDiscrepancyStatus.RESOLVED, issue.getStatus());
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "SHIPPER", "ADMIN"})
    void unrelatedRolesDoNotInheritManagerHubPowers(String role) throws Exception {
        mvc.perform(get("/manager/trips").with(user("unrelated@example.test").roles(role))).andExpect(status().isForbidden());
        mvc.perform(post("/manager/trips/1/arrive").with(user("unrelated@example.test").roles(role)).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void guestsReceiveExistingHtmlAndJson401Contract() throws Exception {
        mvc.perform(get("/manager/trips").accept("text/html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/manager/trips/1").accept("application/json")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }
    @Test void wrongManagedPointCannotDepartOrReceiveAndUnknownTripNotFound() throws Exception {
        add(); mvc.perform(action("seal", true)).andExpect(status().isForbidden());
        mvc.perform(source(get("/manager/trips/999"))).andExpect(status().isNotFound());
        mvc.perform(source(get("/manager/trips/1"))).andExpect(status().isOk());
        departFromAlreadyAdded(); mvc.perform(action("arrive", false)).andExpect(status().isForbidden()); assertNull(receipt);
    }
    void departFromAlreadyAdded() throws Exception { mvc.perform(action("seal", false)).andExpect(flash().attributeExists("notice")); mvc.perform(action("depart", false)).andExpect(flash().attributeExists("notice")); }
    @Test void inactiveEmployeeAndRevokedOrLockedUserDeniedWithExistingSession() throws Exception {
        arrive(); hubEmployee.setStatus(EmployeeStatus.INACTIVE);
        mvc.perform(action("receipts", true).param("itemId", "101")).andExpect(status().isForbidden()); hubEmployee.setStatus(EmployeeStatus.ACTIVE);
        when(roles.findRoleNamesByUserId(hubActor.getId())).thenReturn(List.of());
        mvc.perform(hub(get("/manager/trips/1"))).andExpect(status().isForbidden());
        when(roles.findRoleNamesByUserId(hubActor.getId())).thenReturn(List.of("ROLE_MANAGER")); hubActor.setStatus(UserStatus.LOCKED);
        // The guard reads the same actor returned from users, not the token's cached role.
        when(users.findByEmail(hubActor.getEmail())).thenReturn(Optional.of(hubActor));
        mvc.perform(action("receipts", true).param("itemId", "101")).andExpect(status().isForbidden());
    }
    @Test void allPostEndpointsRequireCsrf() throws Exception {
        for (String suffix : List.of("items", "seal", "depart", "arrive", "receipts", "discrepancies", "discrepancies/resolve", "close")) {
            mvc.perform(source(post("/manager/trips/1/" + suffix))).andExpect(status().isForbidden());
        }
        mvc.perform(source(post("/manager/trips"))).andExpect(status().isForbidden());
        mvc.perform(source(post("/manager/trips/local-ready"))).andExpect(status().isForbidden()); assertTrue(audit.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"actorId", "employeeId", "destinationId", "status"})
    void clientCannotInjectActorDestinationOrState(String field) throws Exception {
        mvc.perform(action("seal", false).param(field, "999")).andExpect(status().isBadRequest()); assertTrue(audit.isEmpty());
    }
    @Test void targetTripAlwaysComesFromUriAndDuplicateFormRejected() throws Exception {
        mvc.perform(source(post("/manager/trips/1/items")).with(csrf()).param("expectedVersion", "0").param("shipmentIds", "31").param("tripId", "999"))
            .andExpect(flash().attributeExists("notice")); assertEquals(1L, item.getTrip().getId());
        mvc.perform(source(post("/manager/trips/1/items")).with(csrf()).param("expectedVersion", "0").param("shipmentIds", "31"))
            .andExpect(flash().attributeExists("error")); verify(items, times(1)).saveAndFlush(any());
    }
    @Test void foreignManifestItemCannotBeReceivedAndReceiptNeedsActualAcknowledgment() throws Exception {
        arrive(); mvc.perform(action("receipts", true).param("itemId", "999")).andExpect(status().isNotFound());
        mvc.perform(hub(post("/manager/trips/1/receipts")).with(csrf()).param("expectedVersion", trip.getVersion().toString()).param("itemId", "101").param("reference", "REF"))
            .andExpect(flash().attributeExists("error")); assertNull(receipt);
    }
    @Test void publicTrackingNeverContainsTransportOperationalData() throws Exception {
        arrive(); var summary = mock(ShipmentRepository.PublicTrackingSummary.class);
        when(summary.getStatus()).thenReturn(s.getStatus()); when(summary.getUpdatedAt()).thenReturn(s.getUpdatedAt());
        when(shipments.findPublicTrackingSummary(s.getTrackingNumber())).thenReturn(Optional.of(summary));
        when(shipmentEvents.findPublicTrackingEvents(eq(s.getTrackingNumber()), any(), anyString(), any())).thenReturn(List.of());
        mvc.perform(get("/tracking").param("trackingNumber", s.getTrackingNumber())).andExpect(status().isOk()).andExpect(model().attributeExists("result"))
            .andExpect(r -> { String html = r.getResponse().getContentAsString();
                for (String privateValue : List.of(trip.getCode(), "INTERNAL-REFERENCE", sourceActor.getFullName(), s.getSenderPhone())) { assertFalse(html.contains(privateValue)); }
            });
        for (ShipmentEvent e : timeline) {
            var p = mock(ShipmentEventRepository.PublicTrackingEvent.class); when(p.getFromStatus()).thenReturn(e.getFromStatus());
            when(p.getToStatus()).thenReturn(e.getToStatus()); when(p.getTitle()).thenReturn(e.getEventTitle()); when(p.getOccurredAt()).thenReturn(e.getOccurredAt());
            assertFalse(PublicTrackingEventPolicy.allowsEvent(p));
        }
    }
    @Test void sameBranchReadyPageAndActionDoNotCreateTripOrReceipt() throws Exception {
        s.setDestinationBranch(origin); s.setDestinationHub(hub);
        when(items.findLocalReady(eq(sourceActor.getId()), any())).thenReturn(new PageImpl<>(List.of(s)));
        mvc.perform(source(get("/manager/trips/local-ready"))).andExpect(status().isOk());
        mvc.perform(source(post("/manager/trips/local-ready")).with(csrf()).param("shipmentId", "31").param("expectedVersion", "0").param("confirmed", "true").param("reference", "SORT"))
            .andExpect(flash().attributeExists("notice")); assertEquals(ShipmentStatus.AT_DESTINATION_BRANCH, s.getStatus());
        verify(trips, never()).saveAndFlush(any()); verify(receipts, never()).saveAndFlush(any());
    }
    @Test void creationFormValidatesDatabaseRouteAndCreatesWithActualActor() throws Exception {
        mvc.perform(source(post("/manager/trips")).with(csrf()).param("routeId", "1").param("requestToken", "b".repeat(32)))
            .andExpect(redirectedUrl("/manager/trips/2")).andExpect(flash().attributeExists("notice"));
        verify(trips).saveAndFlush(argThat(t -> t.getCreatedBy().getId().equals(sourceActor.getId()) && t.getOrigin().getId() == 11L));
    }
}
