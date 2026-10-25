-- Global time-first access paths complement existing branch/parent-leading indexes.
-- No new reporting/audit table, no data backfill and no change to historical migrations.

CREATE INDEX IX_shipments_reports_created ON dbo.shipments (created_at, id);

CREATE INDEX IX_transport_trips_reports_created ON dbo.transport_trips (created_at, id);

CREATE INDEX IX_trip_discrepancies_reports_time ON dbo.trip_discrepancies (recorded_at, id);

CREATE INDEX IX_audit_logs_reports_time ON dbo.audit_logs (created_at DESC, id DESC);

CREATE INDEX IX_shipment_events_audit_time ON dbo.shipment_events (occurred_at DESC, id DESC);

CREATE INDEX IX_transport_trip_events_audit_time ON dbo.transport_trip_events (occurred_at DESC, id DESC);

CREATE INDEX IX_support_ticket_events_audit_time ON dbo.support_ticket_events (occurred_at DESC, id DESC);

CREATE INDEX IX_support_tickets_reports_time ON dbo.support_tickets (created_at, id);

CREATE INDEX IX_return_receipts_reports_time ON dbo.return_receipts (received_at, id);

CREATE INDEX IX_trip_receipts_reports_time ON dbo.trip_receipts (received_at, id);
