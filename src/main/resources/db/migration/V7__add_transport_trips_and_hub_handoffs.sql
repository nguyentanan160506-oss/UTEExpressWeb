-- Additive transport custody; no seeded routes, inferred receipts or legacy updates.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

CREATE TABLE dbo.transport_routes (
    id BIGINT IDENTITY(1,1) NOT NULL,
    origin_id BIGINT NOT NULL,
    destination_id BIGINT NOT NULL,
    leg VARCHAR(30) NOT NULL,
    is_active BIT NOT NULL CONSTRAINT DF_transport_routes_is_active DEFAULT (1),
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_transport_routes_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_transport_routes PRIMARY KEY (id),
    CONSTRAINT FK_transport_routes_origin_id FOREIGN KEY (origin_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_routes_destination_id FOREIGN KEY (destination_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_transport_routes_points UNIQUE (origin_id, destination_id),
    CONSTRAINT CK_transport_routes_points CHECK (origin_id <> destination_id),
    CONSTRAINT CK_transport_routes_leg CHECK (leg IN ('COLLECTION', 'LINEHAUL', 'DISTRIBUTION'))
);
CREATE INDEX IX_transport_routes_origin ON dbo.transport_routes (origin_id, is_active);

CREATE TABLE dbo.transport_trips (
    id BIGINT IDENTITY(1,1) NOT NULL,
    route_id BIGINT NOT NULL,
    origin_id BIGINT NOT NULL,
    destination_id BIGINT NOT NULL,
    leg VARCHAR(30) NOT NULL,
    code VARCHAR(35) NOT NULL,
    request_token VARCHAR(32) NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_transport_trips_status DEFAULT ('DRAFT'),
    version BIGINT NOT NULL CONSTRAINT DF_transport_trips_version DEFAULT (0),
    expected_count INT NOT NULL CONSTRAINT DF_transport_trips_expected_count DEFAULT (0),
    created_by BIGINT NOT NULL,
    sealed_by BIGINT NULL,
    departed_by BIGINT NULL,
    departed_employee_id BIGINT NULL,
    arrived_by BIGINT NULL,
    closed_by BIGINT NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_transport_trips_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_transport_trips_updated_at DEFAULT (SYSUTCDATETIME()),
    sealed_at DATETIME2(7) NULL,
    departed_at DATETIME2(7) NULL,
    arrived_at DATETIME2(7) NULL,
    closed_at DATETIME2(7) NULL,
    CONSTRAINT PK_transport_trips PRIMARY KEY (id),
    CONSTRAINT FK_transport_trips_route_id FOREIGN KEY (route_id) REFERENCES dbo.transport_routes (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trips_origin_id FOREIGN KEY (origin_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trips_destination_id FOREIGN KEY (destination_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trips_created_by FOREIGN KEY (created_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trips_sealed_by FOREIGN KEY (sealed_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trips_departed_by FOREIGN KEY (departed_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trips_departed_employee_id FOREIGN KEY (departed_employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trips_arrived_by FOREIGN KEY (arrived_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trips_closed_by FOREIGN KEY (closed_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_transport_trips_code UNIQUE (code),
    CONSTRAINT UQ_transport_trips_token UNIQUE (request_token),
    CONSTRAINT CK_transport_trips_points CHECK (origin_id <> destination_id),
    CONSTRAINT CK_transport_trips_leg CHECK (leg IN ('COLLECTION', 'LINEHAUL', 'DISTRIBUTION')),
    CONSTRAINT CK_transport_trips_status CHECK (status IN ('DRAFT', 'SEALED', 'DEPARTED', 'RECONCILING', 'CLOSED')),
    CONSTRAINT CK_transport_trips_count CHECK (expected_count >= 0 AND expected_count <= 500),
    CONSTRAINT CK_transport_trips_version CHECK (version >= 0),
    CONSTRAINT CK_transport_trips_seal CHECK (status = 'DRAFT' OR (sealed_by IS NOT NULL AND sealed_at IS NOT NULL AND expected_count > 0)),
    CONSTRAINT CK_transport_trips_depart CHECK (status IN ('DRAFT', 'SEALED') OR (departed_by IS NOT NULL AND departed_employee_id IS NOT NULL AND departed_at IS NOT NULL)),
    CONSTRAINT CK_transport_trips_arrive CHECK (status IN ('DRAFT', 'SEALED', 'DEPARTED') OR (arrived_by IS NOT NULL AND arrived_at IS NOT NULL)),
    CONSTRAINT CK_transport_trips_close CHECK (status <> 'CLOSED' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL))
);
CREATE INDEX IX_transport_trips_origin ON dbo.transport_trips (origin_id, status, created_at DESC);
CREATE INDEX IX_transport_trips_destination ON dbo.transport_trips (destination_id, status, created_at DESC);

CREATE TABLE dbo.transport_trip_items (
    id BIGINT IDENTITY(1,1) NOT NULL,
    trip_id BIGINT NOT NULL,
    shipment_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_transport_trip_items_status DEFAULT ('MANIFESTED'),
    is_active BIT NOT NULL CONSTRAINT DF_transport_trip_items_is_active DEFAULT (1),
    added_at DATETIME2(7) NOT NULL CONSTRAINT DF_transport_trip_items_added_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_transport_trip_items PRIMARY KEY (id),
    CONSTRAINT FK_transport_trip_items_trip_id FOREIGN KEY (trip_id) REFERENCES dbo.transport_trips (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trip_items_shipment_id FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_trip_items_manifest UNIQUE (trip_id, shipment_id),
    CONSTRAINT CK_trip_items_status CHECK (status IN ('MANIFESTED', 'RECEIVED', 'MISSING', 'DAMAGED', 'REJECTED')),
    CONSTRAINT CK_trip_items_active CHECK ((status = 'RECEIVED' AND is_active = 0) OR (status IN ('MANIFESTED', 'MISSING', 'DAMAGED', 'REJECTED') AND is_active = 1))
);
CREATE INDEX IX_trip_items_trip_status ON dbo.transport_trip_items (trip_id, status);
CREATE INDEX IX_trip_items_shipment ON dbo.transport_trip_items (shipment_id, added_at DESC);

CREATE TABLE dbo.trip_receipts (
    id BIGINT IDENTITY(1,1) NOT NULL,
    item_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    reference NVARCHAR(100) NOT NULL,
    notes NVARCHAR(500) NULL,
    received_at DATETIME2(7) NOT NULL CONSTRAINT DF_trip_receipts_received_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_trip_receipts PRIMARY KEY (id),
    CONSTRAINT FK_trip_receipts_item_id FOREIGN KEY (item_id) REFERENCES dbo.transport_trip_items (id) ON DELETE NO ACTION,
    CONSTRAINT FK_trip_receipts_branch_id FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_trip_receipts_actor_id FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_trip_receipts_employee_id FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_trip_receipts_item UNIQUE (item_id),
    CONSTRAINT CK_trip_receipts_reference CHECK (LEN(LTRIM(RTRIM(reference))) > 0)
);
CREATE INDEX IX_trip_receipts_branch ON dbo.trip_receipts (branch_id, received_at DESC);

CREATE TABLE dbo.trip_discrepancies (
    id BIGINT IDENTITY(1,1) NOT NULL,
    trip_id BIGINT NOT NULL,
    item_id BIGINT NULL,
    kind VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_trip_discrepancies_status DEFAULT ('OPEN'),
    reference NVARCHAR(100) NOT NULL,
    notes NVARCHAR(500) NOT NULL,
    actor_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    recorded_at DATETIME2(7) NOT NULL CONSTRAINT DF_trip_discrepancies_recorded_at DEFAULT (SYSUTCDATETIME()),
    resolved_by BIGINT NULL,
    resolved_employee_id BIGINT NULL,
    resolved_at DATETIME2(7) NULL,
    resolution NVARCHAR(500) NULL,
    CONSTRAINT PK_trip_discrepancies PRIMARY KEY (id),
    CONSTRAINT FK_trip_discrepancies_trip_id FOREIGN KEY (trip_id) REFERENCES dbo.transport_trips (id) ON DELETE NO ACTION,
    CONSTRAINT FK_trip_discrepancies_item_id FOREIGN KEY (item_id) REFERENCES dbo.transport_trip_items (id) ON DELETE NO ACTION,
    CONSTRAINT FK_trip_discrepancies_actor_id FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_trip_discrepancies_employee_id FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_trip_discrepancies_resolved_by FOREIGN KEY (resolved_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_trip_discrepancies_resolved_employee_id FOREIGN KEY (resolved_employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT CK_trip_discrepancies_kind CHECK (kind IN ('MISSING', 'DAMAGED', 'REJECTED', 'UNEXPECTED')),
    CONSTRAINT CK_trip_discrepancies_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT CK_trip_discrepancies_item CHECK ((kind = 'UNEXPECTED' AND item_id IS NULL) OR (kind IN ('MISSING', 'DAMAGED', 'REJECTED') AND item_id IS NOT NULL)),
    CONSTRAINT CK_trip_discrepancies_text CHECK (LEN(LTRIM(RTRIM(reference))) > 0 AND LEN(LTRIM(RTRIM(notes))) > 0),
    CONSTRAINT CK_trip_discrepancies_resolution CHECK ((status = 'OPEN' AND resolved_by IS NULL AND resolved_employee_id IS NULL AND resolved_at IS NULL AND resolution IS NULL) OR (status = 'RESOLVED' AND resolved_by IS NOT NULL AND resolved_employee_id IS NOT NULL AND resolved_at IS NOT NULL AND resolution IS NOT NULL AND LEN(LTRIM(RTRIM(resolution))) > 0))
);
CREATE INDEX IX_trip_discrepancies_trip ON dbo.trip_discrepancies (trip_id, status, recorded_at DESC);
CREATE INDEX IX_trip_discrepancies_item ON dbo.trip_discrepancies (item_id, status);

CREATE TABLE dbo.transport_trip_events (
    id BIGINT IDENTITY(1,1) NOT NULL,
    trip_id BIGINT NOT NULL,
    item_id BIGINT NULL,
    event_type VARCHAR(30) NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    actor_id BIGINT NOT NULL,
    employee_id BIGINT NULL,
    branch_id BIGINT NOT NULL,
    reference NVARCHAR(100) NULL,
    notes NVARCHAR(500) NULL,
    occurred_at DATETIME2(7) NOT NULL CONSTRAINT DF_transport_trip_events_occurred_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_transport_trip_events PRIMARY KEY (id),
    CONSTRAINT FK_transport_trip_events_trip_id FOREIGN KEY (trip_id) REFERENCES dbo.transport_trips (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trip_events_item_id FOREIGN KEY (item_id) REFERENCES dbo.transport_trip_items (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trip_events_actor_id FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trip_events_employee_id FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_transport_trip_events_branch_id FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT CK_transport_trip_events_type CHECK (event_type IN ('CREATED', 'ITEMS_ADDED', 'SEALED', 'DEPARTED', 'ARRIVED', 'ITEM_RECEIVED', 'DISCREPANCY_RECORDED', 'DISCREPANCY_RESOLVED', 'CLOSED')),
    CONSTRAINT CK_transport_trip_events_from CHECK (from_status IS NULL OR from_status IN ('DRAFT', 'SEALED', 'DEPARTED', 'RECONCILING', 'CLOSED')),
    CONSTRAINT CK_transport_trip_events_to CHECK (to_status IN ('DRAFT', 'SEALED', 'DEPARTED', 'RECONCILING', 'CLOSED'))
);
CREATE INDEX IX_transport_trip_events_timeline ON dbo.transport_trip_events (trip_id, occurred_at DESC);

-- A reservation remains active for missing/rejected/damaged items until physical receipt.
CREATE UNIQUE INDEX UX_trip_items_active_shipment ON dbo.transport_trip_items (shipment_id) WHERE is_active = 1;
CREATE UNIQUE INDEX UX_trip_discrepancies_open_item ON dbo.trip_discrepancies (item_id) WHERE item_id IS NOT NULL AND status = 'OPEN';
