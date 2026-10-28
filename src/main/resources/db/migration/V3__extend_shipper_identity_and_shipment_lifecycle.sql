-- Additive logistics foundation. No inferred user links, route data, fees or historical events.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

INSERT INTO dbo.roles (name)
SELECT 'ROLE_SHIPPER'
WHERE NOT EXISTS (SELECT 1 FROM dbo.roles WHERE name = 'ROLE_SHIPPER');

ALTER TABLE dbo.employees ADD user_id BIGINT NULL;
GO
ALTER TABLE dbo.employees ADD CONSTRAINT FK_employees_user FOREIGN KEY (user_id) REFERENCES dbo.users (id) ON DELETE NO ACTION;
-- Optional links must permit multiple NULLs while preventing two employees sharing one login.
CREATE UNIQUE INDEX UX_employees_user_nonnull ON dbo.employees (user_id) WHERE user_id IS NOT NULL;

ALTER TABLE dbo.branches ADD parent_hub_id BIGINT NULL;
GO
ALTER TABLE dbo.branches ADD CONSTRAINT FK_branches_parent_hub FOREIGN KEY (parent_hub_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION;
ALTER TABLE dbo.branches ADD CONSTRAINT CK_branches_parent_hub CHECK (parent_hub_id IS NULL OR (branch_type = 'LOCAL_BRANCH' AND parent_hub_id <> id));
CREATE INDEX IX_branches_parent_hub ON dbo.branches (parent_hub_id);
-- Whether the referenced branch is an ACTIVE CENTRAL_HUB is checked by the network service.

CREATE TABLE dbo.branch_service_areas (
    id BIGINT IDENTITY(1,1) NOT NULL,
    branch_id BIGINT NOT NULL,
    province NVARCHAR(100) NOT NULL,
    district NVARCHAR(100) NOT NULL,
    ward NVARCHAR(100) NOT NULL CONSTRAINT DF_branch_service_areas_ward DEFAULT (''),
    shipment_method VARCHAR(30) NOT NULL,
    is_active BIT NOT NULL CONSTRAINT DF_branch_service_areas_active DEFAULT (1),
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_branch_service_areas_created DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_branch_service_areas_updated DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_branch_service_areas PRIMARY KEY (id),
    CONSTRAINT FK_branch_service_areas_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_branch_service_areas_scope UNIQUE (branch_id, province, district, ward, shipment_method),
    CONSTRAINT CK_branch_service_areas_method CHECK (shipment_method IN ('HOME_PICKUP', 'BRANCH_DROPOFF'))
);
CREATE INDEX IX_branch_service_areas_lookup ON dbo.branch_service_areas (province, district, shipment_method, is_active);
-- Empty ward explicitly represents district-wide coverage; no area rows are seeded.

-- Nullable fields keep old shipments and the Commit 06 form compatible until Commit 09.
ALTER TABLE dbo.shipments ADD shipment_method VARCHAR(30) NULL;
ALTER TABLE dbo.shipments ADD origin_hub_id BIGINT NULL;
ALTER TABLE dbo.shipments ADD destination_hub_id BIGINT NULL;
-- SQL Server initializes existing rows to 0. Hibernate owns subsequent version increments.
ALTER TABLE dbo.shipments ADD version BIGINT NOT NULL CONSTRAINT DF_shipments_version DEFAULT (0);
GO
ALTER TABLE dbo.shipments ADD CONSTRAINT FK_shipments_origin_hub FOREIGN KEY (origin_hub_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION;
ALTER TABLE dbo.shipments ADD CONSTRAINT FK_shipments_destination_hub FOREIGN KEY (destination_hub_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION;
ALTER TABLE dbo.shipments ADD CONSTRAINT CK_shipments_method CHECK (shipment_method IS NULL OR shipment_method IN ('HOME_PICKUP', 'BRANCH_DROPOFF'));
ALTER TABLE dbo.shipments ADD CONSTRAINT CK_shipments_version CHECK (version >= 0);
ALTER TABLE dbo.shipments DROP CONSTRAINT CK_shipments_status;
ALTER TABLE dbo.shipments ADD CONSTRAINT CK_shipments_status CHECK (status IN ('CREATED', 'CONFIRMED', 'PICKUP_ASSIGNED', 'PICKED_UP', 'AT_ORIGIN_BRANCH', 'AT_ORIGIN_HUB', 'IN_TRANSIT', 'AT_DESTINATION_HUB', 'AT_DESTINATION_BRANCH', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'RETURNING', 'RETURNED', 'CANCELLED'));
ALTER TABLE dbo.shipment_events ADD CONSTRAINT CK_shipment_events_from_status CHECK (from_status IS NULL OR from_status IN ('CREATED', 'CONFIRMED', 'PICKUP_ASSIGNED', 'PICKED_UP', 'AT_ORIGIN_BRANCH', 'AT_ORIGIN_HUB', 'IN_TRANSIT', 'AT_DESTINATION_HUB', 'AT_DESTINATION_BRANCH', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'RETURNING', 'RETURNED', 'CANCELLED'));
ALTER TABLE dbo.shipment_events ADD CONSTRAINT CK_shipment_events_to_status CHECK (to_status IN ('CREATED', 'CONFIRMED', 'PICKUP_ASSIGNED', 'PICKED_UP', 'AT_ORIGIN_BRANCH', 'AT_ORIGIN_HUB', 'IN_TRANSIT', 'AT_DESTINATION_HUB', 'AT_DESTINATION_BRANCH', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'RETURNING', 'RETURNED', 'CANCELLED'));
