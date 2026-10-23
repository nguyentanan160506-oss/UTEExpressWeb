package com.ute.uteexpress.notifications;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.notifications.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.*;

/** Stateful persistence boundaries, not JDBC. Used with real policy/service/identity code. */
public class NotificationScenario implements AutoCloseable {
    public static final Instant TIME = Instant.parse("2026-10-23T09:00:00Z");
    public final Clock clock = Clock.fixed(TIME, ZoneOffset.UTC);
    public final UserRepository users = mock(UserRepository.class);
    public final UserRoleRepository roles = mock(UserRoleRepository.class);
    public final NotificationRepository notices = mock(NotificationRepository.class);
    public final ShipmentEventRepository events = mock(ShipmentEventRepository.class);
    public final ManagerBranchRepository managers = mock(ManagerBranchRepository.class);
    public final Map<Long, User> accounts = new LinkedHashMap<>();
    public final Map<Long, List<String>> grants = new LinkedHashMap<>();
    public final Map<Long, Set<Long>> memberships = new LinkedHashMap<>();
    public final Map<Long, Notification> rows = new LinkedHashMap<>();
    public final Map<Long, ShipmentEvent> eventRows = new LinkedHashMap<>();
    public final Branch origin = branch(11), destination = branch(12), hub = branch(13);
    public final User owner = user(1, "ROLE_USER"), other = user(2, "ROLE_USER"), manager = user(3, "ROLE_MANAGER"), secondManager = user(4, "ROLE_MANAGER"), admin = user(5, "ROLE_ADMIN");
    public final Employee courier = courier(21, 6), replacement = courier(22, 7);
    public final Shipment shipment = new Shipment();
    public final NotificationIdentity identity = new NotificationIdentity(users, roles);
    public final ShipmentNotificationProcessor processor = new ShipmentNotificationProcessor(events, notices, roles, managers, clock);
    public final NotificationService service = new NotificationService(notices, identity, clock);
    public NotificationScenario() {
        shipment.setId(31L); shipment.setSender(owner); shipment.setOriginBranch(origin); shipment.setDestinationBranch(destination); shipment.setTrackingNumber("UTE202610230000001");
        memberships.put(manager.getId(), new HashSet<>(Set.of(origin.getId()))); memberships.put(secondManager.getId(), new HashSet<>(Set.of(origin.getId())));
        when(users.findByEmail(anyString())).thenAnswer(c -> accounts.values().stream().filter(u -> u.getEmail().equals(c.getArgument(0))).findFirst());
        when(roles.findRoleNamesByUserId(anyLong())).thenAnswer(c -> grants.getOrDefault(c.getArgument(0), List.of()));
        when(managers.findNotificationManagers(anyLong(), any())).thenAnswer(c -> {
            Long branch = c.getArgument(0); Pageable p = c.getArgument(1);
            return accounts.values().stream().filter(u -> active(u, "MANAGER") && memberships.getOrDefault(u.getId(), Set.of()).contains(branch))
                .skip(p.getOffset()).limit(p.getPageSize()).toList();
        });
        when(events.lockNotificationWork(anyLong())).thenAnswer(c -> Optional.ofNullable(eventRows.get(c.getArgument(0))));
        when(events.saveAndFlush(any())).thenAnswer(c -> { ShipmentEvent e = c.getArgument(0); if (e.getId() == null) e.setId((long) eventRows.size() + 1); eventRows.put(e.getId(), e); return e; });
        AtomicLong ids = new AtomicLong();
        when(notices.saveAndFlush(any())).thenAnswer(c -> { Notification n = c.getArgument(0); if (n.getId() == null) n.setId(ids.incrementAndGet()); rows.put(n.getId(), n); return n; });
        when(notices.existsByUserIdAndSourceEventIdAndAudience(anyLong(), anyLong(), anyString())).thenAnswer(c -> rows.values().stream().anyMatch(n -> n.getUser().getId().equals(c.getArgument(0)) && n.getSourceEvent().getId().equals(c.getArgument(1)) && n.getAudience().equals(c.getArgument(2))));
        when(notices.findVisibleById(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(rows.get(c.getArgument(0))).filter(n -> visible(n, c.getArgument(1))));
        when(notices.findVisible(anyLong(), anyBoolean(), any())).thenAnswer(c -> {
            Long u = c.getArgument(0); boolean unread = c.getArgument(1); Pageable p = c.getArgument(2);
            var all = rows.values().stream().filter(n -> visible(n, u) && (!unread || !n.isRead())).sorted(Comparator.comparing(Notification::getCreatedAt).thenComparing(Notification::getId).reversed()).toList();
            return new PageImpl<>(all.stream().skip(p.getOffset()).limit(p.getPageSize()).toList(), p, all.size());
        });
        when(notices.unreadCount(anyLong())).thenAnswer(c -> rows.values().stream().filter(n -> visible(n, c.getArgument(0)) && !n.isRead()).count());
        when(notices.markRead(anyLong(), anyLong(), any())).thenAnswer(c -> {
            Notification n = rows.get(c.getArgument(0)); if (n == null || !visible(n, c.getArgument(1)) || n.isRead()) return 0;
            n.setRead(true); n.setReadAt(c.getArgument(2)); return 1;
        });
        when(notices.markAllRead(anyLong(), any())).thenAnswer(c -> { int count = 0; for (Notification n : rows.values()) { if (visible(n, c.getArgument(0)) && !n.isRead()) { n.setRead(true); n.setReadAt(c.getArgument(1)); count++; } } return count; });
        when(notices.lockDelivery(anyLong())).thenAnswer(c -> Optional.ofNullable(rows.get(c.getArgument(0))));
    }
    public boolean active(User u, String role) { return u != null && u.getStatus() == UserStatus.ACTIVE && grants.getOrDefault(u.getId(), List.of()).contains("ROLE_" + role); }
    public boolean visible(Notification n, Long u) {
        if (!n.getUser().getId().equals(u) || n.getUser().getStatus() != UserStatus.ACTIVE) return false;
        if (n.getSourceEvent() == null && n.getRelatedShipment() == null) return true;
        return switch (n.getAudience()) {
            case "USER" -> active(n.getUser(), "USER") && n.getRelatedShipment().getSender().getId().equals(u);
            case "MANAGER" -> active(n.getUser(), "MANAGER") && n.getScopeBranch().getStatus() == BranchStatus.ACTIVE && memberships.getOrDefault(u, Set.of()).contains(n.getScopeBranch().getId());
            case "SHIPPER" -> active(n.getUser(), "SHIPPER") && n.getAssignment().getEmployee().getUser().getId().equals(u)
                && n.getAssignment().getEmployee().getStatus() == EmployeeStatus.ACTIVE && n.getAssignment().getEmployee().getPosition() == EmployeePosition.COURIER_DRIVER
                && n.getAssignment().getEmployee().getBranch().getId().equals(n.getScopeBranch().getId()) && n.getScopeBranch().getStatus() == BranchStatus.ACTIVE;
            default -> false;
        };
    }
    public User user(long id, String... role) { User u = new User(); u.setId(id); u.setEmail("u" + id + "@example.test"); u.setStatus(UserStatus.ACTIVE); accounts.put(id, u); grants.put(id, List.of(role)); return u; }
    public Employee courier(long id, long user) { Employee e = new Employee(); e.setId(id); e.setUser(user(user, "ROLE_SHIPPER")); e.setBranch(origin); e.setPosition(EmployeePosition.COURIER_DRIVER); e.setStatus(EmployeeStatus.ACTIVE); return e; }
    public static Branch branch(long id) { Branch b = new Branch(); b.setId(id); b.setStatus(BranchStatus.ACTIVE); return b; }
    public ShipmentAssignment assignment(long id, Employee employee, AssignmentType type) { ShipmentAssignment a = new ShipmentAssignment(); a.setId(id); a.setEmployee(employee); a.setShipment(shipment); a.setAssignmentType(type); a.setStatus(AssignmentStatus.ASSIGNED); return a; }
    public ShipmentEvent event(String title, ShipmentStatus from, ShipmentStatus to) { ShipmentEvent e = new ShipmentEvent(); e.setId((long) eventRows.size() + 1); e.setShipment(shipment); e.setBranch(origin); e.setEventTitle(title); e.setFromStatus(from); e.setToStatus(to); e.setOccurredAt(LocalDateTime.ofInstant(TIME, ZoneOffset.UTC)); eventRows.put(e.getId(), e); return e; }
    public void as(User u) { SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u.getEmail(), null, grants.getOrDefault(u.getId(), List.of()).toArray(String[]::new))); }
    @Override public void close() { SecurityContextHolder.clearContext(); }
}
