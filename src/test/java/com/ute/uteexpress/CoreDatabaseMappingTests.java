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
 * compares them to V1. The SQL Server integration test is separately opt-in.
 */
class CoreDatabaseMappingTests {

    private static final List<Class<?>> ENTITIES = List.of(
        User.class, Role.class, UserRole.class, EmailOtp.class, UserAddress.class,
        Branch.class, ManagerBranch.class, Employee.class, ShippingService.class,
        ShippingRateRule.class, Shipment.class, ShipmentEvent.class,
        ShipmentAssignment.class, DeliveryAttempt.class, ShipmentPayment.class,
        Notification.class, SupportTicket.class, AuditLog.class
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
    void everyTableAndColumnMatchesV1IncludingSqlTypesAndDefaults() {
        assertEquals(18, schema.size());
        assertEquals(schema.keySet(), metadata.collectTableMappings().stream()
            .map(org.hibernate.mapping.Table::getName).collect(Collectors.toSet()));
        assertEquals(18, metadata.getEntityBindings().size());
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
    void foreignKeysUniqueConstraintsIndexesAndChecksMatchV1() {
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
        assertEquals(27, foreignKeys);
        assertEquals(12, uniqueKeys);
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
                assertNull(field.getAnnotation(Version.class));
            }
        }
        assertEquals(27, associations);
        assertEquals(17, identities);
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
                    var match = Pattern.compile("^" + column.name() + " IN \\((.*)\\)$")
                        .matcher(String.join("\n", table.checks.values()));
                    // Check each named SQL CHECK independently; event statuses have no CHECK in V1.
                    for (String check : table.checks.values()) {
                        match.reset(check);
                        if (match.matches()) {
                            Set<String> allowed = Pattern.compile("'([^']+)'").matcher(match.group(1))
                                .results().map(m -> m.group(1)).collect(Collectors.toSet());
                            Set<String> actual = Arrays.stream(field.getType().getEnumConstants())
                                .map(v -> ((Enum<?>) v).name()).collect(Collectors.toSet());
                            assertEquals(allowed, actual, field.toString());
                        }
                    }
                }
            }
        }
        assertEquals(2, maxColumns);
        assertEquals(22, enumColumns);
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
                if (expected == null) {
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
                    assertEquals(expected.equals("1"), flag);
                } else if (actual instanceof BigDecimal amount) {
                    assertEquals(0, new BigDecimal(expected).compareTo(amount));
                } else if (actual instanceof Enum<?> value) {
                    assertEquals(expected.replace("'", ""), value.name());
                } else {
                    assertEquals(expected, actual.toString());
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
    void all18SpringDataRepositoriesInitializeAgainstHibernate() throws Exception {
        try (EntityManager entityManager = sessionFactory.createEntityManager()) {
            var factory = new JpaRepositoryFactory(entityManager);
            for (Class<?> entity : ENTITIES) {
                Class<?> repository = Class.forName("com.ute.uteexpress.repository."
                    + entity.getSimpleName() + "Repository");
                assertNotNull(factory.getRepository(repository));
            }
        }
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
                    continue;
                }
                var fk = Pattern.compile("^CONSTRAINT (\\w+) FOREIGN KEY \\((\\w+)\\)"
                    + " REFERENCES dbo\\.(\\w+) \\(id\\) ON DELETE NO ACTION$").matcher(entry);
                if (fk.matches()) {
                    table.foreignKeys.put(fk.group(1), fk.group(2) + "->" + fk.group(3));
                    continue;
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
                    continue;
                }
                var check = Pattern.compile("^CONSTRAINT (\\w+) CHECK \\(([\\s\\S]*)\\)$")
                    .matcher(entry);
                assertTrue(check.matches(), "Unrecognized V1 definition");
                table.checks.put(check.group(1), normalize(check.group(2)));
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
