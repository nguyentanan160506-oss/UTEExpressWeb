package com.ute.uteexpress;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.id.UserRoleId;
import jakarta.persistence.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boots the actual Hibernate mappings without JDBC access or schema export, and
 * compares them to the cumulative V1 + V3 + V4 + V5 + V6 + V7 + V8 + V9 + V10 schema. The SQL Server integration test is separately opt-in.
 */
class CoreDatabaseMappingTests {

    private static final List<Class<?>> ENTITIES = List.of(
        User.class, Role.class, UserRole.class, EmailOtp.class, UserAddress.class,
        Branch.class, ManagerBranch.class, Employee.class, ShippingService.class,
        ShippingRateRule.class, Shipment.class, ShipmentEvent.class,
        ShipmentAssignment.class, DeliveryAttempt.class, ShipmentPayment.class,
        Notification.class, SupportTicket.class, AuditLog.class, BranchServiceArea.class, PickupFeeRule.class, ShipmentQuote.class, ShipmentIntake.class, PickupEvidence.class, OriginHandoff.class, TransportRoute.class, TransportTrip.class, TransportTripItem.class, TripReceipt.class, TripDiscrepancy.class, TransportTripEvent.class, DeliveryDispatch.class, DeliveryProof.class, DeliveryRecovery.class, RedeliveryDecision.class, ReturnRequest.class, ReturnReceipt.class, PricingVersion.class, SupportTicketAssignment.class, SupportTicketMessage.class, SupportTicketEvent.class, SupportTicketRoute.class
    );

    private static StandardServiceRegistry registry;
    private static MetadataImplementor metadata;
    private static SessionFactoryImplementor sessionFactory;
    private static Map<String, SqlTable> schema;

    @BeforeAll
    static void bootstrapHibernateWithoutDatabase() throws Exception {
        try (var input = CoreDatabaseMappingTests.class.getResourceAsStream(
                "/db/migration/V1__init_logistics_schema.sql")) {
            assertNotNull(input);
            schema = parseSchema(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (String migration : List.of("V3__extend_shipper_identity_and_shipment_lifecycle.sql",
                "V4__add_pickup_pricing_and_shipment_quotes.sql", "V5__add_pickup_assignment_guard_and_origin_intakes.sql", "V6__add_pickup_evidence_and_origin_handoffs.sql", "V7__add_transport_trips_and_hub_handoffs.sql", "V8__add_last_mile_delivery_and_returns.sql", "V9__add_service_catalog_and_pricing_management.sql", "V10__add_shipment_notifications.sql", "V11__add_operations_tickets.sql", "V12__add_customer_support_portal.sql")) {
            try (var input = CoreDatabaseMappingTests.class.getResourceAsStream("/db/migration/" + migration)) {
                assertNotNull(input);
                String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                schema.putAll(parseSchema(sql));
                var alter = Pattern.compile("ALTER TABLE dbo\\.(\\w+) (ADD|DROP CONSTRAINT) ([^;]+);").matcher(sql);
                while (alter.find()) {
                    SqlTable table = schema.get(alter.group(1));
                    if (alter.group(2).equals("DROP CONSTRAINT")) { table.checks.remove(alter.group(3)); table.uniqueKeys.remove(alter.group(3)); }
                    else { for (String definition : splitTopLevel(alter.group(3))) { applyDefinition(table, definition); } }
                }
                var index = Pattern.compile("CREATE INDEX (\\w+) ON dbo\\.(\\w+) \\(([^;]+)\\);").matcher(sql);
                while (index.find()) { schema.get(index.group(2)).indexes.put(index.group(1), normalize(index.group(3))); }
                // Filter predicates cannot be represented by JPA's portable @Index.
                if (migration.startsWith("V3")) {
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_employees_user_nonnull ON dbo.employees (user_id) WHERE user_id IS NOT NULL;"));
                    assertTrue(sql.contains("WHERE NOT EXISTS (SELECT 1 FROM dbo.roles WHERE name = 'ROLE_SHIPPER')"));
                } else if (migration.startsWith("V4")) {
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_shipments_quote_nonnull ON dbo.shipments (quote_id) WHERE quote_id IS NOT NULL;"));
                    assertFalse(sql.contains("INSERT INTO"));
                    assertFalse(sql.contains("UPDATE dbo.shipments"));
                }
                if (migration.startsWith("V12")) {
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_support_ticket_messages_request ON dbo.support_ticket_messages (request_token) WHERE request_token IS NOT NULL;"));
                    assertFalse(sql.contains("INSERT INTO")); assertFalse(sql.contains("UPDATE dbo.")); assertFalse(sql.contains("DELETE FROM"));
                }
                if (migration.startsWith("V10")) {
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_notifications_source_recipient_audience ON dbo.notifications (source_event_id, user_id, audience) WHERE source_event_id IS NOT NULL;"));
                    assertTrue(sql.contains("notification_enabled BIT NOT NULL CONSTRAINT DF_shipment_events_notification_enabled DEFAULT (0)"));
                    assertFalse(sql.contains("INSERT INTO")); assertFalse(sql.contains("UPDATE dbo."));
                    assertFalse(sql.contains("DROP ")); assertFalse(sql.contains("DELETE FROM"));
                }
                if (migration.startsWith("V5")) {
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_assignments_active_pickup ON dbo.shipment_assignments (shipment_id)"));
                    assertTrue(sql.contains("WHERE assignment_type = 'PICKUP' AND status IN ('ASSIGNED', 'IN_PROGRESS')"));
                    assertTrue(sql.contains("THROW 51000"));
                    assertFalse(sql.contains("INSERT INTO"));
                    assertFalse(sql.contains("UPDATE dbo."));
                }
                assertFalse(sql.contains("INSERT INTO dbo.users"));
                if (migration.startsWith("V9")) {
                    assertFalse(sql.contains("UPDATE dbo.")); assertFalse(sql.contains("INSERT INTO"));
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_rate_rules_version_scope"));
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_pickup_rules_version_branch"));
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_pickup_legacy_start"));
                    assertTrue(sql.contains("WHERE pricing_version_id IS NOT NULL"));
                    assertTrue(sql.contains("DROP CONSTRAINT UQ_pickup_fee_rules_start"));
                }
                if (migration.startsWith("V8")) {
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_assignments_active_delivery ON dbo.shipment_assignments (shipment_id) WHERE assignment_type = 'DELIVERY' AND status IN ('ASSIGNED', 'IN_PROGRESS');"));
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_delivery_attempts_success ON dbo.delivery_attempts (shipment_id) WHERE status = 'SUCCESS';"));
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_delivery_recoveries_pending ON dbo.delivery_recoveries (shipment_id) WHERE status = 'PENDING';"));
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_delivery_recoveries_received ON dbo.delivery_recoveries (assignment_id) WHERE status = 'RECEIVED';"));
                    assertTrue(sql.contains("THROW 51002")); assertTrue(sql.contains("THROW 51003"));
                    assertFalse(sql.contains("UPDATE dbo.")); assertFalse(sql.contains("INSERT INTO"));
                }
                if (migration.startsWith("V7")) {
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_trip_items_active_shipment ON dbo.transport_trip_items (shipment_id) WHERE is_active = 1;"));
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_trip_discrepancies_open_item ON dbo.trip_discrepancies (item_id) WHERE item_id IS NOT NULL AND status = 'OPEN';"));
                    assertFalse(sql.contains("INSERT INTO"));
                    assertFalse(sql.contains("UPDATE dbo."));
                    assertFalse(sql.contains("ALTER TABLE"));
                }
                if (migration.startsWith("V6")) {
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_pickup_evidence_success ON dbo.pickup_evidence (assignment_id) WHERE result = 'SUCCESS';"));
                    assertTrue(sql.contains("CREATE UNIQUE INDEX UX_origin_handoffs_pending ON dbo.origin_handoffs (shipment_id) WHERE status = 'PENDING';"));
                    assertFalse(sql.contains("INSERT INTO"));
                    assertFalse(sql.contains("UPDATE dbo."));
                    assertFalse(sql.contains("ALTER TABLE"));
                }
            }
        }
        registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .applySetting("hibernate.hbm2ddl.auto", "none")
            .applySetting("jakarta.persistence.schema-generation.database.action", "none")
            .applySetting("hibernate.connection.provider_class",
                "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
            .build();
        var sources = new MetadataSources(registry);
        ENTITIES.forEach(sources::addAnnotatedClass);
        metadata = (MetadataImplementor) sources.buildMetadata();
        metadata.validate();
        sessionFactory = metadata.buildSessionFactory();
    }

    @AfterAll
    static void closeHibernate() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void everyTableAndColumnMatchesMigrationsIncludingSqlTypesAndDefaults() {
        assertEquals(41, schema.size());
        assertEquals(schema.keySet(), metadata.collectTableMappings().stream()
            .map(org.hibernate.mapping.Table::getName).collect(Collectors.toSet()));
        assertEquals(41, metadata.getEntityBindings().size());
        for (PersistentClass entity : metadata.getEntityBindings()) {
            var table = entity.getTable();
            SqlTable expected = schema.get(table.getName());
            assertEquals("dbo", table.getSchema());
            assertEquals(expected.columns.keySet(), table.getColumns().stream()
                .map(Column::getName).collect(Collectors.toSet()), table.getName());
            for (Column column : table.getColumns()) {
                SqlColumn sql = expected.columns.get(column.getName());
                String label = table.getName() + "." + column.getName();
                assertEquals(sql.type, column.getSqlType(metadata).toLowerCase(Locale.ROOT)
                    .replace(" ", ""), label);
                assertEquals(sql.nullable, column.isNullable(), label);
                assertEquals(sql.defaultValue, column.getDefaultValue(), label);
                if (sql.type.startsWith("datetime2")) {
                    assertEquals(7, column.getTemporalPrecision(), label);
                }
            }
            assertEquals(expected.primaryKey, table.getPrimaryKey().getColumns().stream()
                .map(Column::getName).collect(Collectors.toSet()));
        }
    }

    @Test
    void foreignKeysUniqueConstraintsIndexesAndChecksMatchMigrations() {
        int foreignKeys = 0;
        int uniqueKeys = 0;
        for (PersistentClass entity : metadata.getEntityBindings()) {
            var table = entity.getTable();
            SqlTable expected = schema.get(table.getName());
            var actualFks = new TreeMap<String, String>();
            for (var fk : table.getForeignKeyCollection()) {
                actualFks.put(fk.getName(), fk.getColumns().getFirst().getName()
                    + "->" + fk.getReferencedTable().getName());
            }
            assertEquals(expected.foreignKeys, actualFks, table.getName());
            foreignKeys += actualFks.size();

            var actualUnique = new TreeMap<String, Set<String>>();
            for (var unique : table.getUniqueKeys().values()) {
                actualUnique.put(unique.getName(), unique.getColumns().stream()
                    .map(Column::getName).collect(Collectors.toSet()));
            }
            assertEquals(expected.uniqueKeys, actualUnique, table.getName());
            uniqueKeys += actualUnique.size();

            var declared = entity.getMappedClass().getAnnotation(Table.class);
            var declaredIndexes = Arrays.stream(declared.indexes()).collect(
                Collectors.toMap(Index::name, index -> normalize(index.columnList())));
            assertEquals(expected.indexes, declaredIndexes, table.getName());
            assertEquals(expected.indexes.keySet(), table.getIndexes().keySet());

            var actualChecks = table.getChecks().stream().filter(c -> c.getName() != null)
                .collect(Collectors.toMap(c -> c.getName(), c -> normalize(c.getConstraint())));
            assertEquals(expected.checks, actualChecks, table.getName());
        }
        assertEquals(153, foreignKeys);
        assertEquals(28, uniqueKeys);
    }

    @Test
    void associationsAreLazyAndHaveNoCascadesOrExtraMappings() {
        int associations = 0;
        int identities = 0;
        for (Class<?> entity : ENTITIES) {
            SqlTable table = schema.get(entity.getAnnotation(Table.class).name());
            assertDoesNotThrow(() -> entity.getDeclaredConstructor());
            for (Field field : entity.getDeclaredFields()) {
                ManyToOne association = field.getAnnotation(ManyToOne.class);
                if (association != null) {
                    associations++;
                    JoinColumn join = field.getAnnotation(JoinColumn.class);
                    assertNotNull(join);
                    assertEquals(FetchType.LAZY, association.fetch());
                    assertEquals(0, association.cascade().length);
                    assertEquals(table.columns.get(join.name()).nullable, association.optional());
                    assertEquals(association.optional(), join.nullable());
                    assertEquals(table.foreignKeys.get(join.foreignKey().name()),
                        join.name() + "->" + field.getType().getAnnotation(Table.class).name());
                }
                GeneratedValue generated = field.getAnnotation(GeneratedValue.class);
                if (generated != null) {
                    identities++;
                    assertEquals(GenerationType.IDENTITY, generated.strategy());
                    assertEquals(Long.class, field.getType());
                }
                assertNull(field.getAnnotation(OneToMany.class));
                assertNull(field.getAnnotation(ManyToMany.class));
                if ((entity == Shipment.class || entity == TransportTrip.class || entity == ShippingService.class || entity == PricingVersion.class || entity == SupportTicket.class) && field.getName().equals("version")) {
                    assertNotNull(field.getAnnotation(Version.class));
                    assertEquals(Long.class, field.getType());
                    assertTrue(metadata.getEntityBinding(entity.getName()).isVersioned());
                } else { assertNull(field.getAnnotation(Version.class)); }
            }
        }
        assertEquals(153, associations);
        assertEquals(40, identities);
    }

    @Test
    void runtimeJdbcTypesKeepUnicodeMaxEnumsAndDateTimeCompatibleWithSqlServer() {
        int maxColumns = 0;
        int enumColumns = 0;
        for (Class<?> entity : ENTITIES) {
            var descriptor = sessionFactory.getMappingMetamodel().getEntityDescriptor(entity);
            SqlTable table = schema.get(entity.getAnnotation(Table.class).name());
            for (Field field : entity.getDeclaredFields()) {
                jakarta.persistence.Column column = field.getAnnotation(jakarta.persistence.Column.class);
                if (column == null || field.isAnnotationPresent(Id.class)) {
                    continue;
                }
                SqlColumn sql = table.columns.get(column.name());
                var mapping = (BasicValuedModelPart) descriptor.findAttributeMapping(field.getName());
                int jdbcType = mapping.getJdbcMapping().getJdbcType().getJdbcTypeCode();
                if (sql.type.startsWith("nvarchar")) {
                    assertEquals(SqlTypes.NVARCHAR, jdbcType, field.toString());
                    if (sql.type.equals("nvarchar(max)")) {
                        maxColumns++;
                        assertEquals(String.class, field.getType());
                        assertFalse(mapping.getJdbcMapping().getJdbcType().isLob());
                    }
                } else if (sql.type.startsWith("varchar")) {
                    assertEquals(SqlTypes.VARCHAR, jdbcType, field.toString());
                } else if (sql.type.startsWith("datetime2")) {
                    assertEquals(SqlTypes.TIMESTAMP, jdbcType, field.toString());
                    assertEquals(LocalDateTime.class, field.getType());
                } else if (sql.type.equals("bit")) {
                    // Hibernate's BooleanJdbcType handles BIT using JDBC boolean access.
                    // The exact SQL Server BIT DDL is checked against V1 in the column test.
                    assertTrue(jdbcType == SqlTypes.BIT || jdbcType == SqlTypes.BOOLEAN,
                        field.toString());
                }
                if (field.getType().isEnum()) {
                    enumColumns++;
                    assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
                    var match = Pattern.compile("^(?:" + column.name() + " IS NULL OR )?" + column.name() + " IN \\(('[^']+'(?:,\\s*'[^']+')*)\\)$")
                        .matcher(String.join("\n", table.checks.values()));
                    // Only a complete membership CHECK defines the enum domain. Conditional lifecycle
                    // CHECKs such as status IN ('DRAFT', 'SEALED') OR ... define additional state rules.
                    boolean domainFound = false;
                    for (String check : table.checks.values()) {
                        match.reset(check);
                        if (match.matches()) {
                            domainFound = true;
                            Set<String> allowed = Pattern.compile("'([^']+)'").matcher(match.group(1))
                                .results().map(m -> m.group(1)).collect(Collectors.toSet());
                            Set<String> actual = Arrays.stream(field.getType().getEnumConstants())
                                .map(v -> ((Enum<?>) v).name()).collect(Collectors.toSet());
                            assertEquals(allowed, actual, field.toString());
                        }
                    }
                    assertTrue(domainFound, "Missing complete enum domain CHECK: " + field);
                }
            }
        }
        assertEquals(2, maxColumns);
        assertEquals(53, enumColumns);
    }

    @Test
    void lifecycleDefaultsMatchSqlAndUseUtcAt100NanosecondPrecision() throws Exception {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        for (Class<?> entity : ENTITIES) {
            Object instance = entity.getDeclaredConstructor().newInstance();
            SqlTable table = schema.get(entity.getAnnotation(Table.class).name());
            // Null optional reference defaults must be restored before INSERT.
            for (Field field : entity.getDeclaredFields()) {
                var column = field.getAnnotation(jakarta.persistence.Column.class);
                if (column != null && table.columns.get(column.name()).defaultValue != null
                        && !field.getType().isPrimitive()) {
                    field.setAccessible(true);
                    field.set(instance, null);
                }
            }
            invokeCallbacks(instance, PrePersist.class);
            for (Field field : entity.getDeclaredFields()) {
                var column = field.getAnnotation(jakarta.persistence.Column.class);
                if (column == null) {
                    continue;
                }
                String expected = table.columns.get(column.name()).defaultValue;
                if (expected == null || field.isAnnotationPresent(Version.class)) {
                    continue;
                }
                field.setAccessible(true);
                Object actual = field.get(instance);
                assertNotNull(actual, field.toString());
                if (actual instanceof LocalDateTime timestamp) {
                    assertFalse(timestamp.isBefore(before));
                    assertFalse(timestamp.isAfter(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)));
                    assertEquals(0, timestamp.getNano() % 100);
                } else if (actual instanceof Boolean flag) {
                    // New Java events opt in; SQL DEFAULT(0) intentionally excludes legacy/direct-SQL events.
                    assertEquals(entity == ShipmentEvent.class && field.getName().equals("notificationEnabled") || expected.equals("1"), flag);
                } else if (actual instanceof BigDecimal amount) {
                    assertEquals(0, new BigDecimal(expected).compareTo(amount));
                } else if (actual instanceof Enum<?> value) {
                    assertEquals(expected.replace("'", ""), value.name());
                } else {
                    assertEquals(expected.startsWith("'") && expected.endsWith("'") ? expected.substring(1, expected.length() - 1) : expected, actual.toString());
                }
            }
        }
    }

    @Test
    void callbacksPreserveSuppliedValuesAndRefreshOnlyUpdatedTimestamp() throws Exception {
        LocalDateTime supplied = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        for (Class<?> entity : ENTITIES) {
            Object instance = entity.getDeclaredConstructor().newInstance();
            for (Field field : entity.getDeclaredFields()) {
                if (field.getType() == LocalDateTime.class) {
                    field.setAccessible(true);
                    field.set(instance, supplied);
                }
            }
            invokeCallbacks(instance, PrePersist.class);
            for (Field field : entity.getDeclaredFields()) {
                if (field.getType() == LocalDateTime.class) {
                    field.setAccessible(true);
                    assertEquals(supplied, field.get(instance));
                }
            }
            invokeCallbacks(instance, PreUpdate.class);
            for (Field field : entity.getDeclaredFields()) {
                if (field.getType() == LocalDateTime.class) {
                    field.setAccessible(true);
                    if (field.getName().equals("updatedAt")) {
                        assertTrue(((LocalDateTime) field.get(instance)).isAfter(supplied));
                    } else {
                        assertEquals(supplied, field.get(instance));
                    }
                }
            }
        }
        Shipment shipment = new Shipment();
        shipment.setMaxDeliveryAttempts(7);
        shipment.setCodAmount(new BigDecimal("150000.00"));
        invokeCallbacks(shipment, PrePersist.class);
        assertEquals(7, shipment.getMaxDeliveryAttempts());
        assertEquals(new BigDecimal("150000.00"), shipment.getCodAmount());
        ManagerBranch membership = new ManagerBranch();
        membership.setPrimary(false);
        invokeCallbacks(membership, PrePersist.class);
        assertFalse(membership.isPrimary());
    }

    @Test
    void operationsHqlCompilesWithSqlServerDialectMetadata() {
        try (EntityManager em = sessionFactory.createEntityManager()) {
            for (String hql : com.ute.uteexpress.repository.OperationsDashboardQueries.catalog().values()) {
                assertDoesNotThrow(() -> em.createQuery(hql), hql);
            }
            for (var group : com.ute.uteexpress.dto.operations.OperationsDashboardViews.WorkGroup.values()) {
                String hql = "select s.id,s.trackingNumber,cast(s.status as String),s.createdAt from Shipment s where "
                    + com.ute.uteexpress.repository.OperationsDashboardQueries.workPredicate(group) + " and "
                    + com.ute.uteexpress.repository.OperationsDashboardQueries.GATE + " order by s.createdAt,s.id";
                assertDoesNotThrow(() -> em.createQuery(hql), hql);
            }
            assertDoesNotThrow(() -> em.createQuery(com.ute.uteexpress.repository.OperationsDashboardQueries.tripWorkQuery()));
        }
    }

    @Test
    void compositeKeyAndMapsIdResolveToUserAndRoleIds() throws Exception {
        UserRoleId key = new UserRoleId(11L, 22L);
        assertEquals(key, new UserRoleId(11L, 22L));
        assertEquals(key.hashCode(), new UserRoleId(11L, 22L).hashCode());
        assertNotEquals(key, new UserRoleId(11L, 23L));
        assertNotEquals(key, null);
        assertNotEquals(key, "11:22");
        assertEquals("userId", UserRole.class.getDeclaredField("user")
            .getAnnotation(MapsId.class).value());
        assertEquals("roleId", UserRole.class.getDeclaredField("role")
            .getAnnotation(MapsId.class).value());
        assertEquals(UserRoleId.class, sessionFactory.getMappingMetamodel()
            .getEntityDescriptor(UserRole.class).getIdentifierType().getReturnedClass());
        assertEquals(Set.of("user_id", "role_id"), metadata.getEntityBinding(UserRole.class.getName())
            .getTable().getPrimaryKey().getColumns().stream()
            .map(Column::getName).collect(Collectors.toSet()));
    }

    @Test
    void all36SpringDataRepositoriesInitializeAgainstHibernate() throws Exception {
        try (EntityManager entityManager = sessionFactory.createEntityManager()) {
            var factory = new JpaRepositoryFactory(entityManager);
            for (Class<?> entity : ENTITIES) {
                Class<?> repository = Class.forName("com.ute.uteexpress.repository."
                    + entity.getSimpleName() + "Repository");
                assertNotNull(factory.getRepository(repository));
            }
        }
    }

    @Test
    void hibernateGeneratesVersionPredicateForShipmentUpdatesWithoutExecutingSql() {
        var descriptor = sessionFactory.getMappingMetamodel().getEntityDescriptor(Shipment.class);
        var operation = (org.hibernate.sql.model.PreparableMutationOperation) descriptor.getUpdateCoordinator()
            .getStaticMutationOperationGroup().getSingleOperation();
        String update = operation.getSqlString().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        assertTrue(update.startsWith("updatedbo.shipmentsset"), update);
        assertTrue(update.substring(0, update.indexOf("where")).contains("version=?"), update);
        assertTrue(update.substring(update.indexOf("where")).contains("version=?"), update);
        assertTrue(update.substring(update.indexOf("where")).contains("id=?"), update);
    }

    @Test
    void quoteConsumptionUsesDatabaseLockAndDoesNotUpdateSnapshotColumns() throws Exception {
        var method = com.ute.uteexpress.repository.ShipmentQuoteRepository.class
            .getMethod("findOwnedForUpdate", String.class, Long.class);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, method.getAnnotation(org.springframework.data.jpa.repository.Lock.class).value());
        var descriptor = sessionFactory.getMappingMetamodel().getEntityDescriptor(ShipmentQuote.class);
        var operation = (org.hibernate.sql.model.PreparableMutationOperation) descriptor.getUpdateCoordinator()
            .getStaticMutationOperationGroup().getSingleOperation();
        String update = operation.getSqlString().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        assertEquals("updatedbo.shipment_quotessetconsumed_at=?whereid=?", update);
    }

    private static void invokeCallbacks(Object instance,
            Class<? extends java.lang.annotation.Annotation> annotation) throws Exception {
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotation)) {
                method.setAccessible(true);
                method.invoke(instance);
            }
        }
    }

    private static Map<String, SqlTable> parseSchema(String sql) {
        Map<String, SqlTable> tables = new TreeMap<>();
        var tableMatcher = Pattern.compile("CREATE TABLE dbo\\.(\\w+) \\(([\\s\\S]*?)\\r?\\n\\);")
            .matcher(sql);
        while (tableMatcher.find()) {
            String name = tableMatcher.group(1);
            SqlTable table = new SqlTable();
            for (String entry : splitTopLevel(tableMatcher.group(2))) {
                applyDefinition(table, entry);
            }
            var index = Pattern.compile("CREATE INDEX (\\w+) ON dbo\\." + name
                + "\\s*\\(([^;]+)\\);").matcher(sql);
            while (index.find()) {
                table.indexes.put(index.group(1), normalize(index.group(2)));
            }
            tables.put(name, table);
        }
        return tables;
    }

    private static void applyDefinition(SqlTable table, String entry) {
        var column = Pattern.compile("^(\\w+)\\s+(BIGINT|INT|BIT|VARCHAR|NVARCHAR|DECIMAL|DATETIME2)"
            + "(\\([^)]*\\))?\\s+(?:IDENTITY\\(1,1\\)\\s+)?(NOT NULL|NULL)([\\s\\S]*)$")
            .matcher(entry);
        if (column.matches()) {
            var defaultMatcher = Pattern.compile("DEFAULT \\(([\\s\\S]*)\\)$")
                .matcher(column.group(5));
            table.columns.put(column.group(1), new SqlColumn(
                (column.group(2) + Objects.toString(column.group(3), ""))
                    .toLowerCase(Locale.ROOT).replace(" ", ""),
                column.group(4).equals("NULL"),
                defaultMatcher.find() ? defaultMatcher.group(1) : null));
            return;
        }
        var fk = Pattern.compile("^CONSTRAINT (\\w+) FOREIGN KEY \\((\\w+)\\)"
            + " REFERENCES dbo\\.(\\w+) \\(id\\) ON DELETE NO ACTION$").matcher(entry);
        if (fk.matches()) {
            table.foreignKeys.put(fk.group(1), fk.group(2) + "->" + fk.group(3));
            return;
        }
        var key = Pattern.compile("^CONSTRAINT (\\w+) (PRIMARY KEY|UNIQUE) \\(([^)]+)\\)$")
            .matcher(entry);
        if (key.matches()) {
            Set<String> columns = Arrays.stream(key.group(3).split(","))
                .map(String::trim).collect(Collectors.toSet());
            if (key.group(2).equals("PRIMARY KEY")) {
                table.primaryKey = columns;
            } else {
                table.uniqueKeys.put(key.group(1), columns);
            }
            return;
        }
        var check = Pattern.compile("^CONSTRAINT (\\w+) CHECK \\(([\\s\\S]*)\\)$")
            .matcher(entry);
        assertTrue(check.matches(), "Unrecognized migration definition: " + entry);
        table.checks.put(check.group(1), normalize(check.group(2)));
    }


    private static List<String> splitTopLevel(String body) {
        List<String> entries = new ArrayList<>();
        int start = 0;
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    entries.add(body.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        entries.add(body.substring(start).trim());
        return entries;
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private record SqlColumn(String type, boolean nullable, String defaultValue) {
    }

    private static final class SqlTable {
        private final Map<String, SqlColumn> columns = new TreeMap<>();
        private final Map<String, String> foreignKeys = new TreeMap<>();
        private final Map<String, Set<String>> uniqueKeys = new TreeMap<>();
        private final Map<String, String> checks = new TreeMap<>();
        private final Map<String, String> indexes = new TreeMap<>();
        private Set<String> primaryKey = Set.of();
    }
}
