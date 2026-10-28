-- Extend the existing ticket root without guessing scope/actors for legacy data.

ALTER TABLE dbo.support_tickets DROP CONSTRAINT CK_support_tickets_status;

ALTER TABLE dbo.support_tickets ADD handling_branch_id BIGINT NULL,
    created_by BIGINT NULL,
    source VARCHAR(30) NULL,
    category VARCHAR(30) NULL,
    request_token VARCHAR(32) NULL,
    request_fingerprint VARCHAR(64) NULL,
    resolved_at DATETIME2(7) NULL,
    closed_at DATETIME2(7) NULL,
    warning_due_at DATETIME2(7) NULL,
    version BIGINT NOT NULL CONSTRAINT DF_support_tickets_version DEFAULT (0);
GO

ALTER TABLE dbo.support_tickets ADD CONSTRAINT FK_support_tickets_branch FOREIGN KEY (handling_branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION;

ALTER TABLE dbo.support_tickets ADD CONSTRAINT FK_support_tickets_creator FOREIGN KEY (created_by) REFERENCES dbo.users (id) ON DELETE NO ACTION;

ALTER TABLE dbo.support_tickets ADD CONSTRAINT CK_support_tickets_category CHECK (category IS NULL OR category IN ('DELAY', 'NOT_RECEIVED', 'DELIVERY_FAILURE', 'LOSS_REPORTED', 'DAMAGE_REPORTED', 'WRONG_RECIPIENT', 'ADDRESS', 'SHIPPING_FEE', 'RETURN', 'STAFF_CONDUCT', 'OTHER'));

ALTER TABLE dbo.support_tickets ADD CONSTRAINT CK_support_tickets_source CHECK (source IS NULL OR source IN ('MANAGER_PHONE', 'MANAGER_EMAIL', 'MANAGER_COUNTER', 'CUSTOMER'));

ALTER TABLE dbo.support_tickets ADD CONSTRAINT CK_support_tickets_version CHECK (version >= 0);

ALTER TABLE dbo.support_tickets ADD CONSTRAINT CK_support_tickets_managed CHECK (handling_branch_id IS NULL OR (shipment_id IS NOT NULL AND created_by IS NOT NULL AND category IS NOT NULL AND source IS NOT NULL AND request_token IS NOT NULL AND request_fingerprint IS NOT NULL));

ALTER TABLE dbo.support_tickets ADD CONSTRAINT CK_support_tickets_resolved_time CHECK (resolved_at IS NULL OR (resolved_at >= created_at AND status IN ('RESOLVED', 'CLOSED')));

ALTER TABLE dbo.support_tickets ADD CONSTRAINT CK_support_tickets_closed_time CHECK (closed_at IS NULL OR (closed_at >= created_at AND status = 'CLOSED'));

ALTER TABLE dbo.support_tickets ADD CONSTRAINT CK_support_tickets_warning_time CHECK (warning_due_at IS NULL OR warning_due_at >= created_at);

ALTER TABLE dbo.support_tickets ADD CONSTRAINT CK_support_tickets_status CHECK (status IN ('OPEN', 'ASSIGNED', 'PROCESSING', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED', 'REOPENED'));

CREATE UNIQUE INDEX UX_support_tickets_request ON dbo.support_tickets (request_token) WHERE request_token IS NOT NULL;

CREATE INDEX IX_support_tickets_branch ON dbo.support_tickets (handling_branch_id, status, priority, created_at DESC, id DESC);

CREATE INDEX IX_support_tickets_shipment ON dbo.support_tickets (shipment_id);

CREATE INDEX IX_support_tickets_warning ON dbo.support_tickets (handling_branch_id, warning_due_at, status);

CREATE TABLE dbo.support_ticket_assignments (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    manager_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    ended_by BIGINT NULL,
    reason NVARCHAR(500) NULL,
    assigned_at DATETIME2(7) NOT NULL CONSTRAINT DF_support_ticket_assignments_assigned_at DEFAULT (SYSUTCDATETIME()),
    ended_at DATETIME2(7) NULL,
    CONSTRAINT PK_support_ticket_assignments PRIMARY KEY (id),
    CONSTRAINT FK_support_ticket_assignments_ticket_id FOREIGN KEY (ticket_id) REFERENCES dbo.support_tickets (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_assignments_manager_id FOREIGN KEY (manager_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_assignments_employee_id FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_assignments_ended_by FOREIGN KEY (ended_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT CK_support_ticket_assignments_end CHECK ((ended_at IS NULL AND ended_by IS NULL) OR (ended_at IS NOT NULL AND ended_by IS NOT NULL AND ended_at >= assigned_at))
);

CREATE INDEX IX_support_ticket_assignments_manager ON dbo.support_ticket_assignments (manager_id, ended_at, ticket_id);

CREATE INDEX IX_support_ticket_assignments_history ON dbo.support_ticket_assignments (ticket_id, assigned_at DESC, id DESC);

CREATE TABLE dbo.support_ticket_messages (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    body NVARCHAR(2000) NOT NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_support_ticket_messages_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_support_ticket_messages PRIMARY KEY (id),
    CONSTRAINT FK_support_ticket_messages_ticket_id FOREIGN KEY (ticket_id) REFERENCES dbo.support_tickets (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_messages_author_id FOREIGN KEY (author_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT CK_support_ticket_messages_kind CHECK (kind IN ('INTERNAL', 'PUBLIC')),
    CONSTRAINT CK_support_ticket_messages_body CHECK (LEN(LTRIM(RTRIM(body))) > 0)
);

CREATE INDEX IX_support_ticket_messages_timeline ON dbo.support_ticket_messages (ticket_id, created_at DESC, id DESC);

CREATE TABLE dbo.support_ticket_events (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    assignee_id BIGINT NULL,
    previous_assignee_id BIGINT NULL,
    message_id BIGINT NULL,
    action VARCHAR(30) NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    detail NVARCHAR(1000) NULL,
    occurred_at DATETIME2(7) NOT NULL CONSTRAINT DF_support_ticket_events_occurred_at DEFAULT (SYSUTCDATETIME()),
    processed_at DATETIME2(7) NULL,
    retry_at DATETIME2(7) NULL,
    attempts INT NOT NULL CONSTRAINT DF_support_ticket_events_attempts DEFAULT (0),
    CONSTRAINT PK_support_ticket_events PRIMARY KEY (id),
    CONSTRAINT FK_support_ticket_events_ticket_id FOREIGN KEY (ticket_id) REFERENCES dbo.support_tickets (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_events_branch_id FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_events_actor_id FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_events_assignee_id FOREIGN KEY (assignee_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_events_previous_assignee_id FOREIGN KEY (previous_assignee_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_events_message_id FOREIGN KEY (message_id) REFERENCES dbo.support_ticket_messages (id) ON DELETE NO ACTION,
    CONSTRAINT CK_support_ticket_events_action CHECK (action IN ('CREATED', 'ASSIGNED', 'REASSIGNED', 'PRIORITY_CHANGED', 'CATEGORY_CHANGED', 'STATUS_CHANGED', 'INTERNAL_NOTE', 'PUBLIC_REPLY', 'RESOLVED', 'CLOSED', 'REOPENED')),
    CONSTRAINT CK_support_ticket_events_from CHECK (from_status IS NULL OR from_status IN ('OPEN', 'ASSIGNED', 'PROCESSING', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED', 'REOPENED')),
    CONSTRAINT CK_support_ticket_events_to CHECK (to_status IN ('OPEN', 'ASSIGNED', 'PROCESSING', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED', 'REOPENED')),
    CONSTRAINT CK_support_ticket_events_attempts CHECK (attempts >= 0)
);

CREATE INDEX IX_support_ticket_events_timeline ON dbo.support_ticket_events (ticket_id, occurred_at DESC, id DESC);

CREATE INDEX IX_support_ticket_events_work ON dbo.support_ticket_events (processed_at, retry_at, id);

CREATE UNIQUE INDEX UX_support_ticket_assignments_current ON dbo.support_ticket_assignments (ticket_id) WHERE ended_at IS NULL;

ALTER TABLE dbo.notifications ADD source_ticket_event_id BIGINT NULL, related_ticket_id BIGINT NULL;
GO

ALTER TABLE dbo.notifications ADD CONSTRAINT FK_notifications_ticket_event FOREIGN KEY (source_ticket_event_id) REFERENCES dbo.support_ticket_events (id) ON DELETE NO ACTION;

ALTER TABLE dbo.notifications ADD CONSTRAINT FK_notifications_ticket FOREIGN KEY (related_ticket_id) REFERENCES dbo.support_tickets (id) ON DELETE NO ACTION;

ALTER TABLE dbo.notifications ADD CONSTRAINT CK_notifications_single_source CHECK (source_event_id IS NULL OR source_ticket_event_id IS NULL);

CREATE UNIQUE INDEX UX_notifications_ticket_recipient ON dbo.notifications (source_ticket_event_id, user_id, audience) WHERE source_ticket_event_id IS NOT NULL;

CREATE INDEX IX_shipments_operations_origin ON dbo.shipments (origin_branch_id, created_at, status, id);

CREATE INDEX IX_shipments_operations_destination ON dbo.shipments (destination_branch_id, created_at, status, id);

CREATE INDEX IX_shipments_operations_custody ON dbo.shipments (current_branch_id, status, id);

CREATE INDEX IX_delivery_attempts_operations_period ON dbo.delivery_attempts (attempt_time, shipment_id, status);

CREATE INDEX IX_delivery_proofs_operations_period ON dbo.delivery_proofs (delivered_at, shipment_id);

CREATE INDEX IX_return_requests_operations_period ON dbo.return_requests (requested_at, shipment_id);

CREATE INDEX IX_return_receipts_operations_period ON dbo.return_receipts (branch_id, received_at);

CREATE INDEX IX_trip_receipts_operations_period ON dbo.trip_receipts (branch_id, received_at);

CREATE INDEX IX_support_tickets_operations_period ON dbo.support_tickets (handling_branch_id, created_at);

CREATE INDEX IX_support_tickets_operations_resolution ON dbo.support_tickets (handling_branch_id, resolved_at);

ALTER TABLE dbo.support_tickets ADD CONSTRAINT CK_support_tickets_managed_state CHECK (handling_branch_id IS NULL OR ((status IN ('OPEN', 'ASSIGNED', 'PROCESSING', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'REOPENED') AND resolved_at IS NULL AND closed_at IS NULL) OR (status = 'RESOLVED' AND resolved_at IS NOT NULL AND closed_at IS NULL AND resolution_notes IS NOT NULL AND LEN(LTRIM(RTRIM(resolution_notes))) > 0) OR (status = 'CLOSED' AND resolved_at IS NOT NULL AND closed_at IS NOT NULL AND resolution_notes IS NOT NULL AND LEN(LTRIM(RTRIM(resolution_notes))) > 0)));
ALTER TABLE dbo.support_tickets ADD CONSTRAINT CK_support_tickets_managed_assignee CHECK (handling_branch_id IS NULL OR status = 'REOPENED' OR (status = 'OPEN' AND assigned_manager_id IS NULL) OR (status IN ('ASSIGNED', 'PROCESSING', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED') AND assigned_manager_id IS NOT NULL));
ALTER TABLE dbo.notifications ADD CONSTRAINT CK_notifications_ticket_context CHECK (source_ticket_event_id IS NULL OR (related_ticket_id IS NOT NULL AND notification_type = 'SUPPORT' AND audience IN ('USER', 'MANAGER') AND related_shipment_id IS NULL AND assignment_id IS NULL));
