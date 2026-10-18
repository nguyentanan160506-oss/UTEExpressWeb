-- No inferred receipts/events or reassignment of legacy data.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

-- Fail explicitly if legacy assignments need a business review; do not silently cancel them.
IF EXISTS (SELECT shipment_id FROM dbo.shipment_assignments
    WHERE assignment_type = 'PICKUP' AND status IN ('ASSIGNED', 'IN_PROGRESS')
    GROUP BY shipment_id HAVING COUNT(*) > 1)
    THROW 51000, 'Duplicate active pickup assignments require review before V5.', 1;

CREATE UNIQUE INDEX UX_assignments_active_pickup ON dbo.shipment_assignments (shipment_id)
    WHERE assignment_type = 'PICKUP' AND status IN ('ASSIGNED', 'IN_PROGRESS');

-- Origin receipt only. Destination/trip/hub custody is deferred to Commit 12.
CREATE TABLE dbo.shipment_intakes (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    received_by BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    pickup_assignment_id BIGINT NULL,
    shipment_method VARCHAR(30) NOT NULL,
    receipt_reference NVARCHAR(100) NOT NULL,
    notes NVARCHAR(500) NULL,
    received_at DATETIME2(7) NOT NULL CONSTRAINT DF_shipment_intakes_received DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_shipment_intakes PRIMARY KEY (id),
    CONSTRAINT UQ_shipment_intakes_shipment UNIQUE (shipment_id),
    CONSTRAINT FK_intakes_shipment FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_intakes_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_intakes_actor FOREIGN KEY (received_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_intakes_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_intakes_pickup FOREIGN KEY (pickup_assignment_id) REFERENCES dbo.shipment_assignments (id) ON DELETE NO ACTION,
    CONSTRAINT CK_intakes_method CHECK (shipment_method IN ('HOME_PICKUP', 'BRANCH_DROPOFF')),
    CONSTRAINT CK_intakes_pickup CHECK ((shipment_method = 'BRANCH_DROPOFF' AND pickup_assignment_id IS NULL) OR (shipment_method = 'HOME_PICKUP' AND pickup_assignment_id IS NOT NULL))
);
CREATE INDEX IX_intakes_branch_received ON dbo.shipment_intakes (branch_id, received_at DESC);
