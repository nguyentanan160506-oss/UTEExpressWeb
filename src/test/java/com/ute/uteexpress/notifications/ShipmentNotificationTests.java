package com.ute.uteexpress.notifications;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.notifications.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentNotificationTests {
    NotificationScenario f;
    @BeforeEach void setup() { f = new NotificationScenario(); }
    @AfterEach void close() { f.close(); }
    @Test void createdNotifiesOwnerAndAllEligibleSourceManagersButNotOtherUsersOrAdmin() {
        var e = f.event("Đã tạo vận đơn", null, ShipmentStatus.CREATED); f.processor.process(e.getId());
        assertEquals(Set.of(1L,3L,4L), f.rows.values().stream().map(n -> n.getUser().getId()).collect(java.util.stream.Collectors.toSet()));
        assertNotNull(e.getNotificationProcessedAt()); assertTrue(f.rows.values().stream().allMatch(n -> n.getSourceEvent() == e && n.getNotificationType() == NotificationType.SHIPMENT_STATUS));
    }
    @ParameterizedTest @EnumSource(value=UserStatus.class, names={"LOCKED", "UNVERIFIED"})
    void inactiveOwnerReceivesNothing(UserStatus status) { f.owner.setStatus(status); var e=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);f.processor.process(e.getId());assertTrue(f.rows.values().stream().noneMatch(n -> n.getUser()==f.owner)); }
    @Test void removedUserRoleReceivesNothing() { f.grants.put(1L,List.of());f.processor.process(f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED).getId()); assertTrue(f.rows.values().stream().noneMatch(n -> n.getUser()==f.owner)); }
    @Test void retryAndRestartedProcessorDoNotDuplicatePersistentNotices() {
        var e=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);f.processor.process(e.getId());
        var restarted = new ShipmentNotificationProcessor(f.events,f.notices,f.roles,f.managers,f.clock);
        restarted.process(e.getId());assertEquals(3,f.rows.size());
        e.setNotificationProcessedAt(null);restarted.process(e.getId());assertEquals(3,f.rows.size());
    }
    @Test void oldEventsAreNotBackfilled() { var e=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);e.setNotificationEnabled(false);f.processor.process(e.getId());assertTrue(f.rows.isEmpty()); }
    @ParameterizedTest @ValueSource(strings={"Đã đưa kiện vào manifest","Xe đã tới nơi","Ghi chú nội bộ","Chi nhánh chuẩn bị bàn giao giao hàng"})
    void internalPreparationEventsAreNotUserProgress(String title) { f.processor.process(f.event(title,ShipmentStatus.IN_TRANSIT,ShipmentStatus.IN_TRANSIT).getId());assertTrue(f.rows.isEmpty()); }
    @Test void discrepancyNotifiesOnlyManagersAtActualPointAndNeverClaimsReceipt() {
        f.memberships.put(3L,new HashSet<>(Set.of(f.hub.getId()))); var e=f.event("Ghi nhận sai lệch kiểm đếm",ShipmentStatus.IN_TRANSIT,ShipmentStatus.IN_TRANSIT);e.setBranch(f.hub);f.processor.process(e.getId());
        assertEquals(1,f.rows.size());var n=f.rows.values().iterator().next();assertEquals(3L,n.getUser().getId());assertTrue(n.getTitle().contains("sai lệch"));assertFalse(n.getTitle().contains("đã kiểm nhận"));
    }
    @Test void managerMembershipIsEvaluatedWhenProcessingAndAgainWhenReading() {
        var e=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);f.memberships.remove(4L);f.processor.process(e.getId()); assertEquals(2,f.rows.size());
        f.as(f.manager);Long id=f.service.list(0,20,false).items().getFirst().id();f.memberships.remove(3L);
        assertEquals(0,f.service.count());assertTrue(f.service.list(0,20,false).items().isEmpty());assertThrows(ResponseStatusException.class,()->f.service.target(id));assertThrows(ResponseStatusException.class,()->f.service.read(id));
    }
    @Test void assignmentContextNotCurrentShipmentAssigneeControlsRecipients() {
        var old=f.assignment(41,f.courier,AssignmentType.PICKUP);var next=f.assignment(42,f.replacement,AssignmentType.PICKUP);old.setStatus(AssignmentStatus.CANCELLED);
        var e=f.event("Đã phân công lại lấy hàng",ShipmentStatus.PICKUP_ASSIGNED,ShipmentStatus.PICKUP_ASSIGNED);e.setNotificationAssignment(next);e.setNotificationPreviousAssignment(old);f.processor.process(e.getId());
        assertEquals(2,f.rows.size());assertTrue(f.rows.values().stream().anyMatch(n -> n.getUser()==f.courier.getUser() && n.getAssignment()==old && n.getTitle().contains("người khác")));
        f.as(f.courier.getUser());var n=f.service.list(0,20,false).items().getFirst();assertEquals("/shipper/pickups",f.service.target(n.id()));
    }
    @Test void relinkingEmployeeLoginDoesNotTransferHistoricalAssignmentNotice() {
        var a=f.assignment(41,f.courier,AssignmentType.PICKUP);var e=f.event("Đã phân công lấy hàng",ShipmentStatus.CONFIRMED,ShipmentStatus.PICKUP_ASSIGNED);e.setNotificationAssignment(a);
        f.courier.setUser(f.replacement.getUser());f.processor.process(e.getId());assertTrue(f.rows.isEmpty());
    }
    @Test void revokedShipperCannotReadOldTaskContentOrNavigate() {
        var a=f.assignment(41,f.courier,AssignmentType.PICKUP);var e=f.event("Đã phân công lấy hàng",ShipmentStatus.CONFIRMED,ShipmentStatus.PICKUP_ASSIGNED);e.setNotificationAssignment(a);f.processor.process(e.getId());f.as(f.courier.getUser());Long id=f.service.list(0,20,false).items().getFirst().id();
        f.courier.setStatus(EmployeeStatus.INACTIVE);assertEquals(0,f.service.count());assertThrows(ResponseStatusException.class,()->f.service.target(id));
    }
    @Test void deliveryAssignmentAndRetryNotifyOnlyAssignedCourier() {
        f.courier.setBranch(f.destination);var a=f.assignment(41,f.courier,AssignmentType.DELIVERY);var e=f.event("Manager cho phép giao lại",ShipmentStatus.DELIVERY_FAILED,ShipmentStatus.DELIVERY_FAILED);e.setBranch(f.destination);e.setNotificationAssignment(a);f.processor.process(e.getId());
        assertEquals(Set.of(1L,6L),f.rows.values().stream().map(n->n.getUser().getId()).collect(java.util.stream.Collectors.toSet()));f.as(f.courier.getUser());assertEquals("/shipper/deliveries/41",f.service.target(f.service.list(0,20,false).items().getFirst().id()));
    }
    @ParameterizedTest @EnumSource(value=AssignmentStatus.class,names={"CANCELLED","IN_PROGRESS"})
    void returnNoticeUsesCommittedAssignmentSnapshotRatherThanLaterStatus(AssignmentStatus atEvent) {
        f.courier.setBranch(f.destination);var a=f.assignment(41,f.courier,AssignmentType.DELIVERY);a.setStatus(atEvent);
        var e=f.event("Manager khởi tạo hoàn, chờ thu hồi/vận chuyển thực tế",ShipmentStatus.DELIVERY_FAILED,ShipmentStatus.RETURNING);e.setNotificationAssignment(a);
        a.setStatus(atEvent==AssignmentStatus.CANCELLED?AssignmentStatus.COMPLETED:AssignmentStatus.CANCELLED);f.processor.process(e.getId());
        var notice=f.rows.values().stream().filter(n->"SHIPPER".equals(n.getAudience())).findFirst().orElseThrow();assertEquals(atEvent==AssignmentStatus.CANCELLED?"Nhiệm vụ đã bị hủy do hoàn hàng":"Nhiệm vụ cần xử lý thu hồi kiện",notice.getTitle());
    }
    @ParameterizedTest @CsvSource({"Chi nhánh từ chối bàn giao,PICKED_UP,PICKUP","Chi nhánh nhận bàn giao HOME_PICKUP,AT_ORIGIN_BRANCH,PICKUP","Chi nhánh từ chối nhận lại kiện,DELIVERY_FAILED,DELIVERY","Chi nhánh đã thực nhận lại kiện,RETURNING,DELIVERY"})
    void handoffResultNotifiesOnlyActualCourier(String title,ShipmentStatus status,AssignmentType type) {
        if(type==AssignmentType.DELIVERY)f.courier.setBranch(f.destination);var a=f.assignment(41,f.courier,type);
        var e=f.event(title,status==ShipmentStatus.AT_ORIGIN_BRANCH?ShipmentStatus.PICKED_UP:status,status);e.setNotificationAssignment(a);f.processor.process(e.getId());
        assertEquals(1,f.rows.values().stream().filter(n->"SHIPPER".equals(n.getAudience())).count());assertTrue(f.rows.values().stream().filter(n->"SHIPPER".equals(n.getAudience())).allMatch(n->n.getUser()==f.courier.getUser()));
    }
    @Test void wrongTitleStatusTupleCannotClaimSuccess() { f.processor.process(f.event("Đã giao kiện cho người nhận",ShipmentStatus.IN_TRANSIT,ShipmentStatus.IN_TRANSIT).getId());assertTrue(f.rows.isEmpty()); }
    @Test void eventDescriptionsAndContactsNeverCopiedIntoNotice() { var e=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);e.setDescription("private-phone private-street private-price <script>");f.processor.process(e.getId());assertTrue(f.rows.values().stream().noneMatch(n->(n.getTitle()+n.getMessage()).contains("private-"))); }
    @Test void pagingStableUnreadFilteringAndMarkingAreOwnerScopedAndIdempotent() {
        for(int i=0;i<23;i++)f.processor.process(f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED).getId());f.as(f.owner);
        var first=f.service.list(0,20,false);assertEquals(23,first.totalElements());assertEquals(20,first.items().size());assertTrue(first.items().get(0).id()>first.items().get(1).id());assertEquals(3,f.service.list(1,20,false).items().size());
        Long id=first.items().getFirst().id();f.service.read(id);f.service.read(id);assertNotNull(f.rows.get(id).getReadAt());assertEquals(22,f.service.count());assertEquals(22,f.service.list(0,20,true).totalElements());
        f.as(f.other);assertThrows(ResponseStatusException.class,()->f.service.read(id));assertThrows(ResponseStatusException.class,()->f.service.target(id));f.service.readAll();
        f.as(f.owner);assertEquals(22,f.service.count());f.service.readAll();f.service.readAll();assertEquals(0,f.service.count());assertEquals(23,f.service.list(0,50,false).totalElements());
    }
    @ParameterizedTest @CsvSource({"-1,20","1000001,20","0,0","0,51","0,2147483647"})
    void paginationIsBoundedAtServiceBoundary(int page,int size) { f.as(f.owner);assertThrows(ResponseStatusException.class,()->f.service.list(page,size,false)); }
    @Test void lockedOrRoleRevokedPrincipalCannotUseService() { f.as(f.owner);f.owner.setStatus(UserStatus.LOCKED);assertThrows(AccessDeniedException.class,()->f.service.count());f.owner.setStatus(UserStatus.ACTIVE);f.grants.put(1L,List.of());assertThrows(AccessDeniedException.class,()->f.service.count()); }
    @Test void anonymousCannotResolveDatabaseIdentity() { SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("test","anonymous",List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));assertThrows(AccessDeniedException.class,()->f.service.count()); }
    @Test void socketFailureRetainsSavedNoticeAndRetryHasBoundedDelay() {
        f.processor.process(f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED).getId());var n=f.rows.values().iterator().next();var broker=mock(SimpMessagingTemplate.class);
        doThrow(new IllegalStateException("synthetic broker failure")).when(broker).convertAndSendToUser(anyString(),anyString(),any());var delivery=new NotificationDelivery(f.notices,broker,f.clock);
        assertThrows(IllegalStateException.class,()->delivery.deliver(n.getId()));assertNull(n.getDeliveredAt());assertEquals(3,f.rows.size());delivery.retry(n.getId());assertEquals(1,n.getDeliveryAttempts());assertTrue(n.getDeliveryRetryAt().isAfter(n.getCreatedAt()));assertEquals(300,ShipmentNotificationProcessor.backoff(1000000));
    }
    @Test void deliveryUsesPrivatePrincipalNameAndContentFreeRefresh() {
        f.processor.process(f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED).getId());var n=f.rows.values().iterator().next();var broker=mock(SimpMessagingTemplate.class);var delivery=new NotificationDelivery(f.notices,broker,f.clock);
        delivery.deliver(n.getId());delivery.deliver(n.getId());verify(broker,times(1)).convertAndSendToUser(f.owner.getEmail(),"/queue/notifications",Map.of("type","REFRESH"));assertNotNull(n.getDeliveredAt());
    }
    @Test void scopeRevokedBeforeDeliverySuppressesSocketSignal() { f.processor.process(f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED).getId());var n=f.rows.values().stream().filter(v->v.getUser()==f.manager).findFirst().orElseThrow();f.memberships.remove(3L);var broker=mock(SimpMessagingTemplate.class);new NotificationDelivery(f.notices,broker,f.clock).deliver(n.getId());verifyNoInteractions(broker); }
    @Test void pendingRetryDefersAndUncommittedMissingEventCannotCreateNotices() { f.processor.process(999L);assertTrue(f.rows.isEmpty());var e=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);f.processor.retry(e.getId());f.processor.process(e.getId());assertTrue(f.rows.isEmpty());assertNull(e.getNotificationProcessedAt());assertEquals(1,e.getNotificationAttempts()); }
}
