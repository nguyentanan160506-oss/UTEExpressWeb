package com.ute.uteexpress.service;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Shared-lock model; actual SQL Server lock behavior still requires integration testing. */
class AdminConcurrencyTests {

    @Test void twoAdminsCannotLockEachOtherAndLeaveZeroActiveAdmins() throws Exception {
        race(false);
    }

    @Test void twoAdminsCannotRemoveEachOthersAdminRoleAndLeaveZeroAdmins() throws Exception {
        race(true);
    }

    private void race(boolean removeRole) throws Exception {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        UserRoleRepository memberships = mock(UserRoleRepository.class);
        Map<Long, User> accounts = Map.of(
            1L, AdminUserServiceTests.user(1L, "first@example.test", UserStatus.ACTIVE),
            2L, AdminUserServiceTests.user(2L, "second@example.test", UserStatus.ACTIVE));
        Map<Long, Set<String>> authorities = new ConcurrentHashMap<>();
        for (Long id : accounts.keySet()) {
            Set<String> names = ConcurrentHashMap.newKeySet();
            names.add("ROLE_ADMIN");
            authorities.put(id, names);
        }
        ReentrantLock mutex = new ReentrantLock();
        Role adminRole = AdminUserServiceTests.role(13L, "ROLE_ADMIN");
        Role userRole = AdminUserServiceTests.role(11L, "ROLE_USER");
        when(roles.findByNameForUpdate("ROLE_ADMIN")).thenAnswer(invocation -> {
            if (!mutex.tryLock(10, TimeUnit.SECONDS)) { throw new IllegalStateException("test lock timeout"); }
            return Optional.of(adminRole);
        });
        when(roles.findByNameInOrderByNameAsc(anyCollection())).thenReturn(List.of(adminRole, userRole));
        when(users.findByEmailForUpdate(anyString())).thenAnswer(invocation ->
            accounts.values().stream().filter(user -> user.getEmail().equals(invocation.getArgument(0))).findFirst());
        when(users.findByIdForUpdate(anyLong())).thenAnswer(invocation ->
            Optional.ofNullable(accounts.get(invocation.getArgument(0))));
        when(memberships.findRoleNamesByUserId(anyLong())).thenAnswer(invocation ->
            List.copyOf(authorities.get(invocation.getArgument(0))));
        when(memberships.countUsersWithRoleAndStatus("ROLE_ADMIN", UserStatus.ACTIVE)).thenAnswer(invocation ->
            accounts.values().stream().filter(user -> user.getStatus() == UserStatus.ACTIVE
                && authorities.get(user.getId()).contains("ROLE_ADMIN")).count());
        when(memberships.findRoleSummaries(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().flatMap(id -> authorities.get(id).stream().map(name ->
                AdminUserServiceTests.summary(id, name.equals("ROLE_ADMIN") ? 13L : 11L, name))).toList();
        });
        doAnswer(invocation -> {
            UserRoleId key = invocation.getArgument(0);
            authorities.get(key.getUserId()).remove(key.getRoleId() == 13L ? "ROLE_ADMIN" : "ROLE_USER");
            return null;
        }).when(memberships).deleteById(any(UserRoleId.class));
        when(memberships.save(any(UserRole.class))).thenAnswer(invocation -> {
            UserRole membership = invocation.getArgument(0);
            authorities.get(membership.getUser().getId()).add(membership.getRole().getName());
            return membership;
        });

        AdminUserService target = new AdminUserService(users, roles, memberships);
        var transaction = new TransactionInterceptor();
        transaction.setTransactionManager(new UnlockingTransactions(mutex));
        transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        transaction.afterPropertiesSet();
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(transaction);
        AdminUserService service = (AdminUserService) factory.getProxy();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (long actor : new long[] {1L, 2L}) {
                results.add(workers.submit(() -> {
                    AdminUserServiceTests.authenticate(accounts.get(actor).getEmail(), "ROLE_ADMIN");
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("test start timeout"); }
                        if (removeRole) { service.updateRoles(3L - actor, AdminUserServiceTests.form(11L)); }
                        else { service.lock(3L - actor); }
                        return true;
                    } catch (AccessDeniedException exception) {
                        return false;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            long successful = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) { successful++; }
            }
            assertEquals(1, successful);
            assertEquals(1, accounts.values().stream().filter(user -> user.getStatus() == UserStatus.ACTIVE
                && authorities.get(user.getId()).contains("ROLE_ADMIN")).count());
        } finally {
            workers.shutdownNow();
        }
    }

    private static class UnlockingTransactions extends AbstractPlatformTransactionManager {
        private final ReentrantLock lock;
        UnlockingTransactions(ReentrantLock lock) { this.lock = lock; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { release(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { release(); }
        private void release() { if (lock.isHeldByCurrentThread()) { lock.unlock(); } }
    }
}
