package com.ute.uteexpress.release.v14;

import com.ute.uteexpress.entity.*;
import java.util.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import static org.junit.jupiter.api.Assertions.*;

/** Hibernate parses the actual QA-only override offline; no SQL connection or schema action. */
class V14MappingOverrideTests {
    @Test void qaMappingReallyExcludesV15ColumnsAndRetainsEveryV14Entity()throws Exception {
        var registry=new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect","org.hibernate.dialect.SQLServerDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access",false)
            .applySetting("hibernate.hbm2ddl.auto","none")
            .applySetting("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl").build();
        try {
            var source=new MetadataSources(registry).addResource("orm-v14-qa.xml");
            var scanner=new ClassPathScanningCandidateComponentProvider(false);scanner.addIncludeFilter(new AnnotationTypeFilter(jakarta.persistence.Entity.class));
            for(var candidate:scanner.findCandidateComponents("com.ute.uteexpress.entity"))source.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
            var metadata=source.buildMetadata();assertEquals(41,metadata.getEntityBindings().size());
            for(var type:List.of(Shipment.class,ShipmentQuote.class)) {
                var columns=metadata.getEntityBinding(type.getName()).getTable().getColumns().stream().map(org.hibernate.mapping.Column::getName).toList();
                assertFalse(columns.contains("rate_scope"));assertFalse(columns.contains("base_shipping_fee"));
                assertTrue(columns.contains("shipping_fee"));assertTrue(columns.contains("pickup_fee"));
            }
            var columns=metadata.getEntityBinding(ShipmentIntake.class.getName()).getTable().getColumns().stream().map(org.hibernate.mapping.Column::getName).toList();
            assertTrue(columns.contains("system_receipt_code"));assertTrue(columns.contains("request_version"));
        } finally {StandardServiceRegistryBuilder.destroy(registry);}
    }
}
