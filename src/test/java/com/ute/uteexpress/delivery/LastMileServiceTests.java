package com.ute.uteexpress.delivery;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.transport.TripCommands;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.LogisticsRuleException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual lifecycle and access guards, synthetic repository boundaries; not a database integration test. */
class LastMileServiceTests {
    DeliveryScenario f;
    @BeforeEach void setup() { f = new DeliveryScenario(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    Assign assign() { return new Assign(31L, f.s.getVersion(), 21L, null, null); }
    Action action() { return new Action(f.current().getId(), f.s.getVersion(), true, "ACTUAL-REFERENCE"); }
    Redelivery retry(Long employee) { return new Redelivery(31L, f.s.getVersion(), employee, "Manager retry decision"); }
    SenderReceipt sender() { return new SenderReceipt(31L, f.s.getVersion(), true, ReturnRecipientType.SENDER, "Actual sender", "SENDER-RECEIPT", null); }
    void noResult() { assertTrue(f.attemptRows.isEmpty()); assertTrue(f.proofRows.isEmpty()); assertNull(f.s.getDeliveredAt()); }

    @Test void assignmentDoesNotMoveParcelOrCreateAttempt() {
        f.assign(); assertEquals(AssignmentType.DELIVERY, f.current().getAssignmentType());
        assertEquals(ShipmentStatus.AT_DESTINATION_BRANCH, f.s.getStatus()); assertSame(f.destination, f.s.getCurrentBranch());
        assertEquals(1, f.eventRows.size()); noResult(); assertTrue(f.dispatchRows.isEmpty());
    }
    @Test void destinationStatusWithoutRealReceiptIsInsufficient() {
        f.forwardReceipt = false; assertThrows(LogisticsRuleException.class, () -> f.service.assign(assign()));
        assertTrue(f.tasks.isEmpty()); assertTrue(f.eventRows.isEmpty());
    }
    @ParameterizedTest @EnumSource(value = ShipmentStatus.class, names = {"CREATED", "CONFIRMED", "PICKED_UP", "AT_ORIGIN_BRANCH", "IN_TRANSIT", "AT_DESTINATION_HUB", "DELIVERED", "RETURNING", "RETURNED", "CANCELLED"})
    void assignmentCannotSkipArrivalStage(ShipmentStatus status) {
        f.s.setStatus(status); assertThrows(LogisticsRuleException.class, () -> f.service.assign(assign()));
        assertTrue(f.tasks.isEmpty()); assertTrue(f.eventRows.isEmpty());
    }
    @Test void legacyMissingMethodCannotInferDeliveryCustody() {
        f.s.setShipmentMethod(null); assertThrows(LogisticsRuleException.class, () -> f.service.assign(assign())); assertTrue(f.tasks.isEmpty());
    }
    @Test void localSameBranchNeedsClassificationAndOriginalIntake() {
        f.s.setOriginBranch(f.destination); f.forwardReceipt = false;
        assertThrows(LogisticsRuleException.class, () -> f.service.assign(assign()));
        f.localClassification = true; f.assign(); assertEquals(1, f.tasks.size());
    }
    @Test void wrongManagerBranchIsDenied() {
        f.as(f.sourceManager); assertThrows(AccessDeniedException.class, () -> f.service.assign(assign())); assertTrue(f.tasks.isEmpty());
    }
    @Test void revokedManagerRoleIsDeniedDespiteSession() {
        f.roleRows.put(f.manager.getId(), List.of("ROLE_USER")); assertThrows(AccessDeniedException.class, () -> f.service.assign(assign()));
    }
    @Test void inactiveShipperCannotBeAssigned() {
        f.courier.setStatus(EmployeeStatus.INACTIVE); assertThrows(LogisticsRuleException.class, () -> f.service.assign(assign()));
    }
    @Test void lockedShipperCannotBeAssigned() {
        f.courier.getUser().setStatus(UserStatus.LOCKED); assertThrows(LogisticsRuleException.class, () -> f.service.assign(assign()));
    }
    @Test void shipperNeedsRealDatabaseRole() {
        f.roleRows.put(f.courier.getUser().getId(), List.of("ROLE_USER")); assertThrows(LogisticsRuleException.class, () -> f.service.assign(assign()));
    }
    @Test void wrongBranchCourierIsDenied() {
        f.courier.setBranch(f.origin); assertThrows(LogisticsRuleException.class, () -> f.service.assign(assign()));
    }
    @Test void onlyOneActiveAssignment() {
        f.assign(); assertThrows(LogisticsRuleException.class, () -> f.service.assign(assign())); assertEquals(1, f.tasks.size());
    }
    @Test void preparedAssignmentCanBeReassignedWithAudit() {
        f.prepare(); var old = f.current();
        f.service.assign(new Assign(31L, f.s.getVersion(), 23L, old.getId(), "Courier unavailable"));
        assertEquals(AssignmentStatus.CANCELLED, old.getStatus()); assertNotNull(old.getCompletedAt());
        assertEquals(DeliveryDispatchStatus.CANCELLED, f.dispatchRows.get(old.getId()).getStatus());
        assertEquals(23L, f.current().getEmployee().getId()); assertEquals(2, f.tasks.size()); noResult();
    }
    @Test void reassignmentNeedsReason() {
        f.assign(); assertThrows(LogisticsRuleException.class, () -> f.service.assign(new Assign(31L, f.s.getVersion(), 23L, f.current().getId(), " ")));
    }
    @Test void cannotReassignParcelAlreadyAccepted() {
        f.accept(); f.as(f.manager);
        assertThrows(LogisticsRuleException.class, () -> f.service.assign(new Assign(31L, f.s.getVersion(), 23L, f.current().getId(), "Change courier")));
        assertEquals(1, f.tasks.size());
    }
    @Test void preparationAloneDoesNotTransferCustody() {
        f.prepare(); var d = f.dispatchRows.values().iterator().next();
        assertEquals(DeliveryDispatchStatus.PREPARED, d.getStatus()); assertNull(d.getAcceptedAt()); assertSame(f.destination, f.s.getCurrentBranch()); noResult();
    }
    @Test void preparationRequiresQualifiedEmployee() {
        f.assign(); f.employeeRows.values().stream().filter(e -> e.getUser() == f.manager).forEach(e -> e.setStatus(EmployeeStatus.INACTIVE));
        assertThrows(AccessDeniedException.class, () -> f.service.prepare(new Prepare(31L, f.s.getVersion(), f.current().getId(), true, "OUT", null)));
    }
    @Test void noAcceptanceWithoutPreparedHandoff() {
        f.assign(); f.as(f.courier.getUser()); assertThrows(LogisticsRuleException.class, () -> f.service.accept(action())); assertSame(f.destination, f.s.getCurrentBranch());
    }
    @Test void foreignCourierCannotAcceptTaskById() {
        f.prepare(); f.as(f.otherCourier.getUser()); assertThrows(ResponseStatusException.class, () -> f.service.accept(action())); noResult();
    }
    @Test void explicitAcceptanceAndIndependentParticipantsRequired() {
        f.prepare(); f.as(f.courier.getUser()); var a = action();
        assertThrows(LogisticsRuleException.class, () -> f.service.accept(new Action(a.assignmentId(), a.expectedVersion(), false, a.reference())));
        var d = f.dispatchRows.get(a.assignmentId()); d.setPreparedBy(f.courier.getUser());
        assertThrows(AccessDeniedException.class, () -> f.service.accept(a)); assertNull(d.getAcceptedAt());
    }
    @Test void acceptanceStoresRealCustodyWithoutFakeDelivery() {
        f.accept(); var d = f.dispatchRows.get(f.current().getId());
        assertSame(f.courier.getUser(), d.getAcceptedBy()); assertSame(f.courier, d.getAcceptedEmployee()); assertNotNull(d.getAcceptedAt());
        assertNull(f.s.getCurrentBranch()); assertEquals(AssignmentStatus.IN_PROGRESS, f.current().getStatus()); noResult();
        assertThrows(LogisticsRuleException.class, () -> f.service.accept(action()));
    }
    @Test void cannotStartOrCompleteBeforeActualAcceptance() {
        f.prepare(); f.as(f.courier.getUser());
        assertThrows(LogisticsRuleException.class, () -> f.service.start(action())); assertThrows(LogisticsRuleException.class, () -> f.service.complete(f.complete())); noResult();
    }
    @Test void startDoesNotCreateAttemptResult() {
        f.start(); assertEquals(ShipmentStatus.OUT_FOR_DELIVERY, f.s.getStatus()); noResult();
    }
    @Test void successfulDeliveryHasStructuredProofAndSnapshotUnchanged() {
        var fee = f.s.getTotalFee(); f.start(); var task = f.current(); f.service.complete(f.complete());
        assertEquals(ShipmentStatus.DELIVERED, f.s.getStatus()); assertNotNull(f.s.getDeliveredAt()); assertEquals(1, f.s.getDeliveryAttemptCount());
        assertEquals(AssignmentStatus.COMPLETED, task.getStatus()); assertNotNull(task.getCompletedAt());
        var a = f.attemptRows.getFirst(); var p = f.proofRows.getFirst(); assertEquals(DeliveryAttemptStatus.SUCCESS, a.getStatus());
        assertSame(a, p.getAttempt()); assertSame(task, p.getAssignment()); assertSame(f.courier.getUser(), p.getActor());
        assertEquals("POD-REFERENCE", p.getReference()); assertNotNull(a.getStartedAt()); assertNotNull(p.getDeliveredAt());
        assertNull(a.getRecipientSignature()); assertNull(a.getPhotoProofUrl()); assertEquals(fee, f.s.getTotalFee());
        assertTrue(f.eventRows.stream().anyMatch(e -> e.getToStatus() == ShipmentStatus.DELIVERED));
    }
    @Test void missingProofDataDoesNotCreateSuccess() {
        f.start(); var c = f.complete();
        assertThrows(LogisticsRuleException.class, () -> f.service.complete(new Complete(c.assignmentId(), c.expectedVersion(), true, c.recipientType(), " ", c.reference(), null)));
        assertThrows(LogisticsRuleException.class, () -> f.service.complete(new Complete(c.assignmentId(), c.expectedVersion(), false, c.recipientType(), c.recipientName(), c.reference(), null))); noResult();
    }
    @Test void doubleSuccessAndContradictoryFailureAreRejected() {
        f.start(); var c = f.complete(); f.service.complete(c);
        assertThrows(RuntimeException.class, () -> f.service.complete(c));
        assertThrows(RuntimeException.class, () -> f.service.fail(new Fail(c.assignmentId(), f.s.getVersion(), true, DeliveryFailureCode.ABSENT, "Absent", null)));
        assertEquals(1, f.proofRows.size()); assertEquals(1, f.attemptRows.size());
    }
    @ParameterizedTest @EnumSource(DeliveryFailureCode.class)
    void failedAttemptKeepsCourierAccountable(DeliveryFailureCode code) {
        f.start(); f.service.fail(new Fail(f.current().getId(), f.s.getVersion(), true, code, "Actual failure reason", null));
        assertEquals(ShipmentStatus.DELIVERY_FAILED, f.s.getStatus()); assertEquals(code, f.attemptRows.getFirst().getFailureCode());
        assertNull(f.s.getCurrentBranch()); assertNull(f.s.getDeliveredAt()); assertEquals(AssignmentStatus.IN_PROGRESS, f.current().getStatus()); assertTrue(f.proofRows.isEmpty());
    }
    @Test void failureNeedsReasonAndCanOnlyBeRecordedOnce() {
        f.start(); assertThrows(LogisticsRuleException.class, () -> f.service.fail(new Fail(f.current().getId(), f.s.getVersion(), true, DeliveryFailureCode.ABSENT, " ", null)));
        f.fail(); assertThrows(LogisticsRuleException.class, f::fail); assertEquals(1, f.attemptRows.size());
    }
    @Test void courierCannotRestartWithoutManagerDecision() {
        f.start(); f.fail(); assertThrows(LogisticsRuleException.class, f::startAgain); assertThrows(AccessDeniedException.class, () -> f.service.redelivery(retry(null)));
    }
    @Test void managerCanAuthorizeSameCourierRetryWithHistoryPreserved() {
        f.start(); f.fail(); var first = f.attemptRows.getFirst(); f.as(f.manager); f.service.redelivery(retry(null));
        assertEquals(RedeliveryMode.SAME_COURIER, f.decisionRows.getFirst().getMode()); assertEquals(1, f.tasks.size());
        assertThrows(LogisticsRuleException.class, () -> f.service.redelivery(retry(null)));
        f.startAgain(); f.service.complete(f.complete()); assertSame(first, f.attemptRows.getFirst()); assertEquals(2, f.attemptRows.getLast().getAttemptNumber());
    }
    @Test void retryCannotSwitchCourierWhileParcelStillHeld() {
        f.start(); f.fail(); f.as(f.manager); assertThrows(LogisticsRuleException.class, () -> f.service.redelivery(retry(23L))); assertTrue(f.decisionRows.isEmpty());
    }
    @Test void retryLimitComesFromShipmentPolicy() {
        f.s.setMaxDeliveryAttempts(1); f.start(); f.fail(); f.as(f.manager);
        assertThrows(LogisticsRuleException.class, () -> f.service.redelivery(retry(null))); assertEquals(1, f.attemptRows.size());
        f.requestReturn(); assertEquals(ReturnStage.REQUESTED, f.returnRequest.getStage());
    }
    @Test void inconsistentLegacyCounterFailsClosed() {
        f.s.setDeliveryAttemptCount(1); assertThrows(LogisticsRuleException.class, () -> f.service.assign(assign())); assertTrue(f.tasks.isEmpty());
    }
    @Test void recoveryRequestDoesNotMeanBranchReceivedParcel() {
        f.start(); f.fail(); f.service.requestRecovery(new Recover(f.current().getId(), f.s.getVersion(), null));
        assertNull(f.s.getCurrentBranch()); assertEquals(DeliveryRecoveryStatus.PENDING, f.recoveryRows.getFirst().getStatus());
        assertThrows(LogisticsRuleException.class, () -> f.service.requestRecovery(new Recover(f.current().getId(), f.s.getVersion(), null)));
    }
    @Test void rejectedRecoveryRemainsWithCourierAndCanBeResubmitted() {
        f.start(); f.fail(); f.service.requestRecovery(new Recover(f.current().getId(), f.s.getVersion(), null)); f.as(f.manager);
        f.service.reviewRecovery(new ReviewRecovery(31L, f.s.getVersion(), f.recoveryRows.getFirst().getId(), false, false, null, "Parcel not presented"));
        assertNull(f.s.getCurrentBranch()); assertEquals(DeliveryRecoveryStatus.REJECTED, f.recoveryRows.getFirst().getStatus());
        f.recover(); assertEquals(DeliveryRecoveryStatus.RECEIVED, f.recoveryRows.getLast().getStatus()); assertSame(f.destination, f.s.getCurrentBranch());
    }
    @Test void recoveryRequiresActualReceiptAndIndependentActor() {
        f.start(); f.fail(); f.service.requestRecovery(new Recover(f.current().getId(), f.s.getVersion(), null)); var r = f.recoveryRows.getFirst(); f.as(f.manager);
        assertThrows(LogisticsRuleException.class, () -> f.service.reviewRecovery(new ReviewRecovery(31L, f.s.getVersion(), r.getId(), true, false, "RECEIPT", null)));
        r.setRequestedBy(f.manager); assertThrows(AccessDeniedException.class, () -> f.service.reviewRecovery(new ReviewRecovery(31L, f.s.getVersion(), r.getId(), true, true, "RECEIPT", null)));
        assertNull(f.s.getCurrentBranch());
    }
    @Test void receivedRecoveryAllowsNewCourierButRequiresFreshHandoff() {
        f.start(); f.fail(); f.recover(); assertEquals(AssignmentStatus.FAILED, f.tasks.get(41L).getStatus());
        f.service.redelivery(retry(23L)); assertEquals(RedeliveryMode.NEW_ASSIGNMENT, f.decisionRows.getFirst().getMode()); assertEquals(2, f.tasks.size());
        f.as(f.otherCourier.getUser()); assertThrows(LogisticsRuleException.class, () -> f.service.start(action()));
        f.as(f.manager); f.service.prepare(new Prepare(31L, f.s.getVersion(), f.current().getId(), true, "NEW-OUTBOUND", null));
        f.as(f.otherCourier.getUser()); f.service.accept(action()); f.service.start(action()); f.service.complete(f.complete());
        assertSame(f.otherCourier, f.attemptRows.getLast().getEmployee()); assertEquals(2, f.s.getDeliveryAttemptCount());
    }
    @Test void returnAtBranchCancelsPreparedTaskWithoutFakeRecovery() {
        f.prepare(); var task = f.current(); f.requestReturn();
        assertEquals(AssignmentStatus.CANCELLED, task.getStatus()); assertEquals(ReturnStage.READY_FOR_TRANSPORT, f.returnRequest.getStage());
        assertEquals(ShipmentStatus.RETURNING, f.s.getStatus()); assertTrue(f.recoveryRows.isEmpty()); assertNull(f.s.getReturnedAt());
        assertThrows(LogisticsRuleException.class, f::requestReturn);
    }
    @Test void returnWhileCourierHoldsParcelFreezesDeliveryUntilActualRecovery() {
        f.start(); var complete = f.complete(); f.requestReturn();
        assertEquals(ReturnStage.REQUESTED, f.returnRequest.getStage()); assertNull(f.s.getCurrentBranch()); assertTrue(f.recoveryRows.isEmpty());
        f.as(f.courier.getUser()); assertThrows(LogisticsRuleException.class, () -> f.service.complete(new Complete(complete.assignmentId(), f.s.getVersion(), true, complete.recipientType(), complete.recipientName(), complete.reference(), null)));
        f.as(f.manager); assertThrows(LogisticsRuleException.class, () -> f.service.redelivery(retry(null)));
        f.recover(); assertEquals(ReturnStage.READY_FOR_TRANSPORT, f.returnRequest.getStage()); assertSame(f.destination, f.s.getCurrentBranch()); assertNull(f.s.getReturnedAt());
    }
    @Test void deliveredParcelCannotEnterOrdinaryReturnFlow() {
        f.start(); f.service.complete(f.complete()); assertThrows(LogisticsRuleException.class, f::requestReturn); assertNull(f.returnRequest);
    }
    @Test void reverseTripsNeedConfiguredRoutes() {
        f.requestReturn(); f.routeRows.get(4L).setActive(false);
        assertThrows(LogisticsRuleException.class, () -> f.returnLeg(4L, false)); assertTrue(f.tripItemRows.isEmpty());
    }
    @Test void cannotSkipReturnLegOrReceiveAtWrongPoint() {
        f.requestReturn(); assertThrows(LogisticsRuleException.class, () -> f.returnLeg(5L, false)); assertTrue(f.tripItemRows.isEmpty());
    }
    @Test void unreceivedReturnCannotProceedToNextLegOrSender() {
        f.requestReturn(); var t = f.returnLeg(4L, false);
        assertEquals(ReturnStage.IN_TRANSIT, f.returnRequest.getStage()); assertNull(f.s.getCurrentBranch());
        assertThrows(RuntimeException.class, () -> f.returnLeg(5L, false));
        f.as(f.sourceManager); assertThrows(LogisticsRuleException.class, () -> f.service.returnToSender(sender()));
        f.receiveReturnLeg(t); assertEquals(ReturnStage.READY_FOR_TRANSPORT, f.returnRequest.getStage()); assertSame(f.h2, f.s.getCurrentBranch());
    }
    @Test void fullReverseTransportEndsOnlyAfterActualSenderReceipt() {
        var fee = f.s.getTotalFee(); f.start(); f.fail(); f.requestReturn(); f.recover();
        f.returnLeg(4L, true); f.returnLeg(5L, true); f.returnLeg(6L, true);
        assertEquals(3, f.tripReceiptRows.size()); assertEquals(ReturnStage.AT_ORIGIN, f.returnRequest.getStage());
        assertEquals(ShipmentStatus.RETURNING, f.s.getStatus()); assertSame(f.origin, f.s.getCurrentBranch()); assertNull(f.senderReceipt); assertNull(f.s.getReturnedAt());
        f.as(f.manager); assertThrows(AccessDeniedException.class, () -> f.service.returnToSender(sender()));
        f.as(f.sourceManager); var c = sender();
        assertThrows(LogisticsRuleException.class, () -> f.service.returnToSender(new SenderReceipt(31L, f.s.getVersion(), false, c.recipientType(), c.recipientName(), c.reference(), null)));
        f.service.returnToSender(c); assertEquals(ReturnStage.COMPLETED, f.returnRequest.getStage()); assertEquals(ShipmentStatus.RETURNED, f.s.getStatus());
        assertNotNull(f.s.getReturnedAt()); assertSame(f.sourceManager, f.senderReceipt.getActor()); assertEquals("SENDER-RECEIPT", f.senderReceipt.getReference());
        assertThrows(RuntimeException.class, () -> f.service.returnToSender(c)); assertEquals(fee, f.s.getTotalFee());
    }
    @Test void sameHubReturnUsesOnlyTwoConfiguredLegs() {
        f.destination.setParentHub(f.h1); f.s.setDestinationHub(f.h1);
        f.routeRows.put(4L, com.ute.uteexpress.transport.TransportFixtures.route(4, f.destination, f.h1, TransportLeg.COLLECTION));
        f.requestReturn(); f.returnLeg(4L, true); f.returnLeg(6L, true);
        assertEquals(2, f.tripReceiptRows.size()); assertEquals(ReturnStage.AT_ORIGIN, f.returnRequest.getStage());
        assertEquals(ShipmentStatus.RETURNING, f.s.getStatus()); assertNull(f.s.getReturnedAt());
    }
    @Test void sourceReceiptCannotBeInferredFromCurrentPointAlone() {
        f.requestReturn(); f.returnRequest.setStage(ReturnStage.AT_ORIGIN); f.s.setCurrentBranch(f.origin); f.as(f.sourceManager);
        assertThrows(LogisticsRuleException.class, () -> f.service.returnToSender(sender())); assertNull(f.senderReceipt); assertNull(f.s.getReturnedAt());
    }
    @Test void returnRequiresReasonAndDoesNotWriteFakeEventOnInvalidRequest() {
        assertThrows(LogisticsRuleException.class, () -> f.service.initiateReturn(new ReturnStart(31L, f.s.getVersion(), " ")));
        assertNull(f.returnRequest); assertTrue(f.eventRows.isEmpty()); assertTrue(f.recoveryRows.isEmpty());
    }
    @Test void sameBranchReturnStillNeedsFinalReceipt() {
        f.s.setOriginBranch(f.destination); f.localClassification = true; f.forwardReceipt = false; f.requestReturn();
        assertEquals(ReturnStage.AT_ORIGIN, f.returnRequest.getStage()); assertNull(f.s.getReturnedAt()); f.service.returnToSender(sender());
        assertEquals(ShipmentStatus.RETURNED, f.s.getStatus()); assertTrue(f.tripRows.isEmpty());
    }
    @Test void staleVersionRejectsOperationBeforeWritingHistory() {
        f.assign(); int count = f.eventRows.size();
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> f.service.prepare(new Prepare(31L, 0L, f.current().getId(), true, "OUT", null)));
        assertTrue(f.dispatchRows.isEmpty()); assertEquals(count, f.eventRows.size());
    }
    @Test void historicalTaskDoesNotExposeRecipientContact() {
        f.start(); var id = f.current().getId(); assertNotNull(f.read.shipperDetail(id, 0).recipient());
        f.service.complete(f.complete()); assertNull(f.read.shipperDetail(id, 0).recipient());
    }
    @Test void foreignTaskReadDeniedBeforeSensitiveChildReads() {
        f.assign(); var id = f.current().getId(); f.as(f.otherCourier.getUser()); clearInvocations(f.attempts, f.recoveries, f.dispatches);
        assertThrows(ResponseStatusException.class, () -> f.read.shipperDetail(id, 0)); verifyNoInteractions(f.attempts, f.recoveries, f.dispatches);
    }
    @Test void sourceManagerCanViewReturnButNotUnrelatedDelivery() {
        f.as(f.sourceManager); assertThrows(ResponseStatusException.class, () -> f.read.managerDetail(31L, 0));
        f.requestReturn(); f.as(f.sourceManager); var detail = f.read.managerDetail(31L, 0); assertNotNull(detail.returnInfo()); assertFalse(detail.canAssign());
    }
}
