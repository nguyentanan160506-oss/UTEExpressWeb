-- No historical backfill. New application-created ShipmentEvents explicitly opt in.
ALTER TABLE dbo.shipment_events ADD notification_enabled BIT NOT NULL CONSTRAINT DF_shipment_events_notification_enabled DEFAULT (0),
    notification_processed_at DATETIME2(7) NULL,
    notification_retry_at DATETIME2(7) NULL,
    notification_attempts INT NOT NULL CONSTRAINT DF_shipment_events_notification_attempts DEFAULT (0),
    notification_assignment_id BIGINT NULL,
    notification_previous_assignment_id BIGINT NULL,
    notification_assignee_user_id BIGINT NULL,
    notification_previous_user_id BIGINT NULL,
    notification_assignment_status VARCHAR(30) NULL;
ALTER TABLE dbo.shipment_events ADD CONSTRAINT CK_shipment_events_notification_assignment_status CHECK (notification_assignment_status IS NULL OR notification_assignment_status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'));
ALTER TABLE dbo.shipment_events ADD CONSTRAINT FK_shipment_events_notification_assignment FOREIGN KEY (notification_assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION;
ALTER TABLE dbo.shipment_events ADD CONSTRAINT FK_shipment_events_notification_previous_assignment FOREIGN KEY (notification_previous_assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION;
ALTER TABLE dbo.shipment_events ADD CONSTRAINT FK_shipment_events_notification_assignee_user FOREIGN KEY (notification_assignee_user_id) REFERENCES dbo.users (id) ON DELETE NO ACTION;
ALTER TABLE dbo.shipment_events ADD CONSTRAINT FK_shipment_events_notification_previous_user FOREIGN KEY (notification_previous_user_id) REFERENCES dbo.users (id) ON DELETE NO ACTION;
CREATE INDEX IX_shipment_events_notification_work ON dbo.shipment_events (notification_enabled, notification_processed_at, notification_retry_at, id);

ALTER TABLE dbo.notifications ADD source_event_id BIGINT NULL,
    audience VARCHAR(20) NULL,
    scope_branch_id BIGINT NULL,
    assignment_id BIGINT NULL,
    read_at DATETIME2(7) NULL,
    delivered_at DATETIME2(7) NULL,
    delivery_retry_at DATETIME2(7) NULL,
    delivery_attempts INT NOT NULL CONSTRAINT DF_notifications_delivery_attempts DEFAULT (0);
ALTER TABLE dbo.notifications ADD CONSTRAINT FK_notifications_source_event FOREIGN KEY (source_event_id) REFERENCES dbo.shipment_events (id) ON DELETE NO ACTION;
ALTER TABLE dbo.notifications ADD CONSTRAINT FK_notifications_scope_branch FOREIGN KEY (scope_branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION;
ALTER TABLE dbo.notifications ADD CONSTRAINT FK_notifications_assignment FOREIGN KEY (assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION;
ALTER TABLE dbo.notifications ADD CONSTRAINT CK_notifications_audience CHECK (audience IS NULL OR audience IN ('USER', 'SHIPPER', 'MANAGER'));
-- SQL Server permits legacy NULL source keys without imposing uniqueness on old notifications.
CREATE UNIQUE INDEX UX_notifications_source_recipient_audience ON dbo.notifications (source_event_id, user_id, audience) WHERE source_event_id IS NOT NULL;
CREATE INDEX IX_notifications_inbox ON dbo.notifications (user_id, created_at DESC, id DESC);
CREATE INDEX IX_notifications_delivery ON dbo.notifications (delivered_at, delivery_retry_at, id);
