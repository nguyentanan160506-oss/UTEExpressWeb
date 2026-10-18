-- Origin custody only; no inferred evidence, data backfill or transport trips.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

CREATE TABLE dbo.pickup_evidence (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    result VARCHAR(20) NOT NULL,
    reference NVARCHAR(100) NULL,
    notes NVARCHAR(500) NULL,
    recorded_at DATETIME2(7) NOT NULL CONSTRAINT DF_pickup_evidence_recorded_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_pickup_evidence PRIMARY KEY (id),
    CONSTRAINT FK_pickup_evidence_shipment_id FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_pickup_evidence_assignment_id FOREIGN KEY (assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_pickup_evidence_employee_id FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_pickup_evidence_actor_id FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT CK_pickup_evidence_result CHECK (result IN ('SUCCESS', 'FAILED')),
    CONSTRAINT CK_pickup_evidence_confirmation CHECK ((result = 'SUCCESS' AND reference IS NOT NULL AND LEN(LTRIM(RTRIM(reference))) > 0) OR (result = 'FAILED' AND notes IS NOT NULL AND LEN(LTRIM(RTRIM(notes))) > 0))
);
CREATE INDEX IX_pickup_evidence_assignment ON dbo.pickup_evidence (assignment_id, recorded_at DESC);

CREATE TABLE dbo.origin_handoffs (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    evidence_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    notes NVARCHAR(500) NULL,
    requested_at DATETIME2(7) NOT NULL CONSTRAINT DF_origin_handoffs_requested_at DEFAULT (SYSUTCDATETIME()),
    reviewed_by BIGINT NULL,
    receiver_id BIGINT NULL,
    reviewed_at DATETIME2(7) NULL,
    reason NVARCHAR(500) NULL,
    CONSTRAINT PK_origin_handoffs PRIMARY KEY (id),
    CONSTRAINT FK_origin_handoffs_shipment_id FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_origin_handoffs_assignment_id FOREIGN KEY (assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_origin_handoffs_evidence_id FOREIGN KEY (evidence_id) REFERENCES dbo.pickup_evidence (id) ON DELETE NO ACTION,
    CONSTRAINT FK_origin_handoffs_branch_id FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_origin_handoffs_employee_id FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_origin_handoffs_requested_by FOREIGN KEY (requested_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_origin_handoffs_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_origin_handoffs_receiver_id FOREIGN KEY (receiver_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT CK_origin_handoffs_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT CK_origin_handoffs_review CHECK ((status = 'PENDING' AND reviewed_by IS NULL AND receiver_id IS NULL AND reviewed_at IS NULL AND reason IS NULL) OR (status IN ('ACCEPTED', 'REJECTED') AND reviewed_by IS NOT NULL AND receiver_id IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT CK_origin_handoffs_rejection CHECK (status <> 'REJECTED' OR (reason IS NOT NULL AND LEN(LTRIM(RTRIM(reason))) > 0)),
    CONSTRAINT CK_origin_handoffs_independent CHECK ((reviewed_by IS NULL OR reviewed_by <> requested_by) AND (receiver_id IS NULL OR receiver_id <> employee_id))
);
CREATE INDEX IX_origin_handoffs_branch_status ON dbo.origin_handoffs (branch_id, status, requested_at DESC);
CREATE INDEX IX_origin_handoffs_assignment ON dbo.origin_handoffs (assignment_id, requested_at DESC);

-- Failed attempts remain auditable; at most one successful collection per assignment.
CREATE UNIQUE INDEX UX_pickup_evidence_success ON dbo.pickup_evidence (assignment_id) WHERE result = 'SUCCESS';
CREATE UNIQUE INDEX UX_origin_handoffs_pending ON dbo.origin_handoffs (shipment_id) WHERE status = 'PENDING';
