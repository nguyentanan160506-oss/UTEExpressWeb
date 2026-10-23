-- Additive last-mile custody and returns; never infer historical proof or payment.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF EXISTS (SELECT shipment_id FROM dbo.shipment_assignments WHERE assignment_type = 'DELIVERY' AND status IN ('ASSIGNED', 'IN_PROGRESS') GROUP BY shipment_id HAVING COUNT(*) > 1)
    THROW 51002, 'Duplicate active delivery assignments require business review before V8.', 1;
IF EXISTS (SELECT shipment_id FROM dbo.delivery_attempts WHERE status = 'SUCCESS' GROUP BY shipment_id HAVING COUNT(*) > 1)
    THROW 51003, 'Duplicate successful attempts require business review before V8.', 1;
CREATE UNIQUE INDEX UX_assignments_active_delivery ON dbo.shipment_assignments (shipment_id) WHERE assignment_type = 'DELIVERY' AND status IN ('ASSIGNED', 'IN_PROGRESS');
CREATE UNIQUE INDEX UX_delivery_attempts_success ON dbo.delivery_attempts (shipment_id) WHERE status = 'SUCCESS';

CREATE TABLE dbo.delivery_dispatches (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_delivery_dispatches_status DEFAULT ('PREPARED'),
    prepared_by BIGINT NOT NULL,
    prepared_employee_id BIGINT NOT NULL,
    reference NVARCHAR(100) NOT NULL,
    notes NVARCHAR(500) NULL,
    prepared_at DATETIME2(7) NOT NULL CONSTRAINT DF_delivery_dispatches_prepared_at DEFAULT (SYSUTCDATETIME()),
    accepted_by BIGINT NULL,
    accepted_employee_id BIGINT NULL,
    accepted_at DATETIME2(7) NULL,
    started_at DATETIME2(7) NULL,
    finished_at DATETIME2(7) NULL,
    CONSTRAINT PK_delivery_dispatches PRIMARY KEY (id),
    CONSTRAINT FK_delivery_dispatches_shipment_id FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_dispatches_assignment_id FOREIGN KEY (assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_dispatches_branch_id FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_dispatches_prepared_by FOREIGN KEY (prepared_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_dispatches_prepared_employee_id FOREIGN KEY (prepared_employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_dispatches_accepted_by FOREIGN KEY (accepted_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_dispatches_accepted_employee_id FOREIGN KEY (accepted_employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_delivery_dispatch_assignment UNIQUE (assignment_id),
    CONSTRAINT CK_delivery_dispatch_status CHECK (status IN ('PREPARED', 'ACCEPTED', 'STARTED', 'FAILED', 'RECOVERY_REQUESTED', 'BRANCH_RETURNED', 'RECALL_REQUIRED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT CK_delivery_dispatch_accept CHECK (status IN ('PREPARED', 'CANCELLED') OR (accepted_by IS NOT NULL AND accepted_employee_id IS NOT NULL AND accepted_at IS NOT NULL)),
    CONSTRAINT CK_delivery_dispatch_independent CHECK ((accepted_by IS NULL OR accepted_by <> prepared_by) AND (accepted_employee_id IS NULL OR accepted_employee_id <> prepared_employee_id)),
    CONSTRAINT CK_delivery_dispatch_reference CHECK (LEN(LTRIM(RTRIM(reference))) > 0)
);
CREATE INDEX IX_delivery_dispatch_shipment ON dbo.delivery_dispatches (shipment_id, prepared_at DESC);

CREATE TABLE dbo.delivery_proofs (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    attempt_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    recipient_type VARCHAR(30) NOT NULL,
    recipient_name NVARCHAR(100) NOT NULL,
    reference NVARCHAR(100) NOT NULL,
    notes NVARCHAR(500) NULL,
    delivered_at DATETIME2(7) NOT NULL CONSTRAINT DF_delivery_proofs_delivered_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_delivery_proofs PRIMARY KEY (id),
    CONSTRAINT FK_delivery_proofs_shipment_id FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_proofs_assignment_id FOREIGN KEY (assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_proofs_attempt_id FOREIGN KEY (attempt_id) REFERENCES dbo.delivery_attempts (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_proofs_actor_id FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_proofs_employee_id FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_delivery_proofs_shipment UNIQUE (shipment_id),
    CONSTRAINT UQ_delivery_proofs_attempt UNIQUE (attempt_id),
    CONSTRAINT CK_delivery_proofs_recipient CHECK (recipient_type IN ('RECIPIENT', 'FAMILY', 'AUTHORIZED_REP')),
    CONSTRAINT CK_delivery_proofs_text CHECK (LEN(LTRIM(RTRIM(recipient_name))) > 0 AND LEN(LTRIM(RTRIM(reference))) > 0)
);
CREATE INDEX IX_delivery_proofs_employee ON dbo.delivery_proofs (employee_id, delivered_at DESC);

CREATE TABLE dbo.delivery_recoveries (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_delivery_recoveries_status DEFAULT ('PENDING'),
    notes NVARCHAR(500) NULL,
    requested_at DATETIME2(7) NOT NULL CONSTRAINT DF_delivery_recoveries_requested_at DEFAULT (SYSUTCDATETIME()),
    reviewed_by BIGINT NULL,
    receiver_id BIGINT NULL,
    reference NVARCHAR(100) NULL,
    reason NVARCHAR(500) NULL,
    reviewed_at DATETIME2(7) NULL,
    CONSTRAINT PK_delivery_recoveries PRIMARY KEY (id),
    CONSTRAINT FK_delivery_recoveries_shipment_id FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_recoveries_assignment_id FOREIGN KEY (assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_recoveries_branch_id FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_recoveries_requested_by FOREIGN KEY (requested_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_recoveries_employee_id FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_recoveries_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_recoveries_receiver_id FOREIGN KEY (receiver_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT CK_delivery_recoveries_status CHECK (status IN ('PENDING', 'RECEIVED', 'REJECTED')),
    CONSTRAINT CK_delivery_recoveries_review CHECK (status = 'PENDING' OR (reviewed_by IS NOT NULL AND receiver_id IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT CK_delivery_recoveries_receipt CHECK (status <> 'RECEIVED' OR (reference IS NOT NULL AND LEN(LTRIM(RTRIM(reference))) > 0)),
    CONSTRAINT CK_delivery_recoveries_reason CHECK (status <> 'REJECTED' OR (reason IS NOT NULL AND LEN(LTRIM(RTRIM(reason))) > 0)),
    CONSTRAINT CK_delivery_recoveries_independent CHECK ((reviewed_by IS NULL OR reviewed_by <> requested_by) AND (receiver_id IS NULL OR receiver_id <> employee_id))
);
CREATE INDEX IX_delivery_recoveries_shipment ON dbo.delivery_recoveries (shipment_id, requested_at DESC);
CREATE INDEX IX_delivery_recoveries_branch ON dbo.delivery_recoveries (branch_id, status);

CREATE TABLE dbo.redelivery_decisions (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    after_attempt_number INT NOT NULL,
    mode VARCHAR(30) NOT NULL,
    reason NVARCHAR(500) NOT NULL,
    decided_at DATETIME2(7) NOT NULL CONSTRAINT DF_redelivery_decisions_decided_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_redelivery_decisions PRIMARY KEY (id),
    CONSTRAINT FK_redelivery_decisions_shipment_id FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_redelivery_decisions_assignment_id FOREIGN KEY (assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_redelivery_decisions_actor_id FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_redelivery_decisions_attempt UNIQUE (shipment_id, after_attempt_number),
    CONSTRAINT CK_redelivery_decisions_mode CHECK (mode IN ('SAME_COURIER', 'NEW_ASSIGNMENT')),
    CONSTRAINT CK_redelivery_decisions_attempt CHECK (after_attempt_number >= 1),
    CONSTRAINT CK_redelivery_decisions_reason CHECK (LEN(LTRIM(RTRIM(reason))) > 0)
);
CREATE INDEX IX_redelivery_decisions_shipment ON dbo.redelivery_decisions (shipment_id, decided_at DESC);

CREATE TABLE dbo.return_requests (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    stage VARCHAR(30) NOT NULL,
    reason NVARCHAR(500) NOT NULL,
    requested_at DATETIME2(7) NOT NULL CONSTRAINT DF_return_requests_requested_at DEFAULT (SYSUTCDATETIME()),
    completed_at DATETIME2(7) NULL,
    CONSTRAINT PK_return_requests PRIMARY KEY (id),
    CONSTRAINT FK_return_requests_shipment_id FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_return_requests_requested_by FOREIGN KEY (requested_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_return_requests_shipment UNIQUE (shipment_id),
    CONSTRAINT CK_return_requests_stage CHECK (stage IN ('REQUESTED', 'READY_FOR_TRANSPORT', 'IN_TRANSIT', 'AT_ORIGIN', 'COMPLETED')),
    CONSTRAINT CK_return_requests_reason CHECK (LEN(LTRIM(RTRIM(reason))) > 0),
    CONSTRAINT CK_return_requests_completion CHECK ((stage = 'COMPLETED' AND completed_at IS NOT NULL) OR (stage <> 'COMPLETED' AND completed_at IS NULL))
);
CREATE INDEX IX_return_requests_stage ON dbo.return_requests (stage, requested_at DESC);

CREATE TABLE dbo.return_receipts (
    id BIGINT IDENTITY(1,1) NOT NULL,
    return_request_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    recipient_type VARCHAR(30) NOT NULL,
    recipient_name NVARCHAR(100) NOT NULL,
    reference NVARCHAR(100) NOT NULL,
    notes NVARCHAR(500) NULL,
    received_at DATETIME2(7) NOT NULL CONSTRAINT DF_return_receipts_received_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_return_receipts PRIMARY KEY (id),
    CONSTRAINT FK_return_receipts_return_request_id FOREIGN KEY (return_request_id) REFERENCES dbo.return_requests (id) ON DELETE NO ACTION,
    CONSTRAINT FK_return_receipts_actor_id FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_return_receipts_employee_id FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_return_receipts_branch_id FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_return_receipts_request UNIQUE (return_request_id),
    CONSTRAINT CK_return_receipts_recipient CHECK (recipient_type IN ('SENDER', 'AUTHORIZED_REP')),
    CONSTRAINT CK_return_receipts_text CHECK (LEN(LTRIM(RTRIM(recipient_name))) > 0 AND LEN(LTRIM(RTRIM(reference))) > 0)
);
CREATE INDEX IX_return_receipts_branch ON dbo.return_receipts (branch_id, received_at DESC);

CREATE UNIQUE INDEX UX_delivery_recoveries_pending ON dbo.delivery_recoveries (shipment_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX UX_delivery_recoveries_received ON dbo.delivery_recoveries (assignment_id) WHERE status = 'RECEIVED';

ALTER TABLE dbo.shipments ADD delivered_at DATETIME2(7) NULL;
ALTER TABLE dbo.shipments ADD returned_at DATETIME2(7) NULL;
ALTER TABLE dbo.shipments ADD CONSTRAINT CK_shipments_delivered_time CHECK (delivered_at IS NULL OR status = 'DELIVERED');
ALTER TABLE dbo.shipments ADD CONSTRAINT CK_shipments_returned_time CHECK (returned_at IS NULL OR status = 'RETURNED');
ALTER TABLE dbo.delivery_attempts ADD assignment_id BIGINT NULL;
ALTER TABLE dbo.delivery_attempts ADD actor_id BIGINT NULL;
ALTER TABLE dbo.delivery_attempts ADD failure_code VARCHAR(30) NULL;
ALTER TABLE dbo.delivery_attempts ADD started_at DATETIME2(7) NULL;
ALTER TABLE dbo.delivery_attempts ADD CONSTRAINT FK_delivery_attempts_assignment FOREIGN KEY (assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION;
ALTER TABLE dbo.delivery_attempts ADD CONSTRAINT FK_delivery_attempts_actor FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION;
ALTER TABLE dbo.delivery_attempts ADD CONSTRAINT CK_delivery_attempts_code CHECK (failure_code IS NULL OR failure_code IN ('UNREACHABLE', 'ABSENT', 'REFUSED', 'INVALID_ADDRESS', 'OPERATIONAL', 'DAMAGED'));
ALTER TABLE dbo.delivery_attempts ADD CONSTRAINT CK_delivery_attempts_context CHECK ((assignment_id IS NULL AND actor_id IS NULL AND failure_code IS NULL AND started_at IS NULL) OR (assignment_id IS NOT NULL AND actor_id IS NOT NULL AND started_at IS NOT NULL AND ((status = 'SUCCESS' AND failure_code IS NULL) OR (status = 'FAILED' AND failure_code IS NOT NULL AND failure_reason IS NOT NULL))));
CREATE INDEX IX_delivery_attempts_assignment ON dbo.delivery_attempts (assignment_id, attempt_time DESC);
ALTER TABLE dbo.transport_trip_items ADD return_request_id BIGINT NULL;
ALTER TABLE dbo.transport_trip_items ADD CONSTRAINT FK_trip_items_return_request FOREIGN KEY (return_request_id) REFERENCES dbo.return_requests (id) ON DELETE NO ACTION;
CREATE INDEX IX_trip_items_return_request ON dbo.transport_trip_items (return_request_id);
