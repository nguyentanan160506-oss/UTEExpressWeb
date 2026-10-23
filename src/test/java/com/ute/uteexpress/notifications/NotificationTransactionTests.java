package com.ute.uteexpress.notifications;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.ShipmentStatus;
import com.ute.uteexpress.service.notifications.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring transaction advice with a deterministic row-lock/rollback model. Not JDBC isolation or a killed process. */
class NotificationTransactionTests {
    NotificationScenario f;Tx tx;ShipmentNotificationProcessor processor;
    @BeforeEach void setup(){f=new NotificationScenario();tx=new Tx(f);processor=proxy(f.processor,tx);}
    @AfterEach void close(){f.close();}
    @SuppressWarnings("unchecked") static <T>T proxy(T target,Tx tx){var advice=new TransactionInterceptor();advice.setTransactionManager(tx);advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());advice.afterPropertiesSet();var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(advice);return (T)factory.getProxy();}
    @Test void businessRollbackRemovesSourceAndNoNoticeCanBeCreated(){
        Long[] id={null};new TransactionTemplate(tx).executeWithoutResult(status->{var e=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);id[0]=e.getId();assertTrue(f.rows.isEmpty());status.setRollbackOnly();});
        processor.process(id[0]);assertTrue(f.rows.isEmpty());assertTrue(f.eventRows.isEmpty());assertEquals(1,tx.rollbacks);
    }
    @Test void failedNotificationBatchRollsBackRowsAndAcknowledgementThenCanRetry(){
        var e=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);var fail=new AtomicBoolean(true);
        doAnswer(c->{Notification n=c.getArgument(0);if("MANAGER".equals(n.getAudience())&&fail.getAndSet(false))throw new DataAccessResourceFailureException("Synthetic persistence failure");n.setId((long)f.rows.size()+1);f.rows.put(n.getId(),n);return n;}).when(f.notices).saveAndFlush(any());
        assertThrows(DataAccessResourceFailureException.class,()->processor.process(e.getId()));assertTrue(f.rows.isEmpty());assertNull(e.getNotificationProcessedAt());assertEquals(1,tx.rollbacks);
        processor.process(e.getId());assertEquals(3,f.rows.size());assertNotNull(e.getNotificationProcessedAt());assertEquals(1,tx.commits);
    }
    @Test void committedSourceSurvivesLossOfInMemoryApplicationCallback(){
        Long id=new TransactionTemplate(tx).execute(status->f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED).getId());assertTrue(f.rows.isEmpty());
        // A new processor discovers the persisted pending event, without an application-event callback.
        var restarted=proxy(new ShipmentNotificationProcessor(f.events,f.notices,f.roles,f.managers,f.clock),tx);restarted.process(id);assertEquals(3,f.rows.size());
    }
    @Test void twoWorkersSerializeOnSourceAndCreateOnlyOneSetOfNotices()throws Exception{
        Long id=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED).getId();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->{start.await(3,TimeUnit.SECONDS);processor.process(id);return true;});var b=pool.submit(()->{start.await(3,TimeUnit.SECONDS);processor.process(id);return true;});start.countDown();assertTrue(a.get(5,TimeUnit.SECONDS));assertTrue(b.get(5,TimeUnit.SECONDS));assertEquals(3,f.rows.size());assertEquals(2,tx.commits);}finally{pool.shutdownNow();}
    }
    @Test void deliverySeesPreviouslyCommittedNotificationAndBrokerFailureCannotUndoBusinessSource(){
        var e=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);processor.process(e.getId());assertEquals(1,tx.commits);var broker=mock(SimpMessagingTemplate.class);
        doAnswer(c->{assertTrue(tx.commits>=1);assertNotNull(f.eventRows.get(e.getId()).getNotificationProcessedAt());throw new IllegalStateException("Synthetic socket unavailable");}).when(broker).convertAndSendToUser(anyString(),anyString(),any());
        var delivery=proxy(new NotificationDelivery(f.notices,broker,f.clock),tx);assertThrows(IllegalStateException.class,()->delivery.deliver(1L));assertEquals(3,f.rows.size());assertNotNull(e.getNotificationProcessedAt());assertEquals(1,tx.rollbacks);
    }
    @Test void sourceIdOrderDoesNotLoseLateCommittedEarlierIdentity(){
        var low=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);var high=f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);
        processor.process(high.getId());processor.process(low.getId());assertEquals(6,f.rows.size());assertNotNull(low.getNotificationProcessedAt());
    }
    @Test void realWorkerReconcilesBoundedBatchesWithoutVolatileCallbacksOrDuplicateNotices(){
        for(int i=0;i<55;i++)f.event("Đã tạo vận đơn",null,ShipmentStatus.CREATED);
        when(f.events.findNotificationWork(any(),any())).thenAnswer(c->{org.springframework.data.domain.Pageable page=c.getArgument(1);return f.eventRows.values().stream().filter(e->e.getNotificationProcessedAt()==null).map(ShipmentEvent::getId).limit(page.getPageSize()).toList();});
        when(f.notices.findDeliveryWork(any(),any())).thenAnswer(c->{org.springframework.data.domain.Pageable page=c.getArgument(1);return f.rows.values().stream().filter(n->n.getDeliveredAt()==null).map(Notification::getId).limit(page.getPageSize()).toList();});
        var worker=new NotificationWorker(f.events,f.notices,processor,proxy(new NotificationDelivery(f.notices,mock(SimpMessagingTemplate.class),f.clock),tx),f.clock);
        worker.poll();assertEquals(30,f.eventRows.values().stream().filter(e->e.getNotificationProcessedAt()!=null).count());assertEquals(90,f.rows.size());
        worker.poll();assertEquals(55,f.eventRows.values().stream().filter(e->e.getNotificationProcessedAt()!=null).count());assertEquals(165,f.rows.size());
        worker.poll();assertEquals(165,f.rows.size());f.as(f.owner);assertEquals(55,f.service.count());
    }
    static class State {Map<Long,Notification> rows;Map<Long,ShipmentEvent> events;Map<Long,LocalDateTime> processed=new HashMap<>();}
    static class Tx extends AbstractPlatformTransactionManager {
        final NotificationScenario f;final ReentrantLock gate=new ReentrantLock();int commits,rollbacks;
        Tx(NotificationScenario f){this.f=f;}
        @Override protected Object doGetTransaction(){return new State();}
        @Override protected void doBegin(Object transaction,TransactionDefinition definition){gate.lock();State state=(State)transaction;state.rows=new LinkedHashMap<>(f.rows);state.events=new LinkedHashMap<>(f.eventRows);f.eventRows.forEach((id,e)->state.processed.put(id,e.getNotificationProcessedAt()));}
        @Override protected void doCommit(DefaultTransactionStatus status){commits++;}
        @Override protected void doRollback(DefaultTransactionStatus status){rollbacks++;State state=(State)status.getTransaction();f.rows.clear();f.rows.putAll(state.rows);f.eventRows.clear();f.eventRows.putAll(state.events);f.eventRows.forEach((id,e)->e.setNotificationProcessedAt(state.processed.get(id)));}
        @Override protected void doCleanupAfterCompletion(Object transaction){gate.unlock();}
    }
}
