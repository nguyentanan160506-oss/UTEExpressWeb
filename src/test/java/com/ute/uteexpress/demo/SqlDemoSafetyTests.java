package com.ute.uteexpress.demo;

import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Offline only: no credentials imported and no network connections. */
class SqlDemoSafetyTests {
    @org.junit.jupiter.api.Test void newV15CheckoutCannotUseV14OnlySqlDemoWritePolicy() {
        for (String mode : java.util.List.of("migrate", "seed", "run")) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> SqlDemoApplication.requireCompatibleSchemaPolicy(mode));
        }
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> SqlDemoApplication.requireCompatibleSchemaPolicy("inspect"));
    }
    private Map<String,String> env(String approval){return Map.of("UTE_SQL_DEMO_APPROVED",approval,
        "UTE_SQL_DEMO_USERNAME","uteexpress_demo","UTE_SQL_DEMO_PASSWORD",java.util.UUID.randomUUID().toString());}
    @ParameterizedTest @ValueSource(strings={"migrate","seed","run"})
    void allWriteModesRequireExactDemoApproval(String mode){
        assertThrows(IllegalArgumentException.class,()->SqlDemoTarget.requireApproval(mode,env("UTEExpress_Test")));
        assertDoesNotThrow(()->SqlDemoTarget.requireApproval(mode,env("UTEExpress_Demo")));
    }
    @Test void inspectDoesNotRequireWriteApprovalButRejectsQaLogin(){
        assertDoesNotThrow(()->SqlDemoTarget.requireApproval("inspect",env("")));
        assertThrows(IllegalArgumentException.class,()->SqlDemoTarget.requireApproval("inspect",Map.of("UTE_SQL_DEMO_USERNAME","uteexpress_qa","UTE_SQL_DEMO_PASSWORD","unused")));
    }
    @ParameterizedTest @ValueSource(strings={"clean","repair","reset","upgrade","unknown"})
    void destructiveModesDoNotExist(String mode){assertThrows(IllegalArgumentException.class,()->SqlDemoTarget.requireApproval(mode,env("UTEExpress_Demo")));}
    @Test void verifiesEveryIdentityDimensionBeforeExposingConnection()throws Exception {
        assertDoesNotThrow(()->SqlDemoTarget.requireIdentity("UTEExpress_Demo","UTEExpress_Demo","SQLEXPRESS","LAPTOP","LAPTOP","uteexpress_demo",true));
        for(int changed=0;changed<7;changed++) {
            String[] values={"UTEExpress_Demo","UTEExpress_Demo","SQLEXPRESS","LAPTOP","LAPTOP","uteexpress_demo"};
            if(changed<6)values[changed]="OTHER";
            boolean metadata=changed!=6;
            assertThrows(SQLException.class,()->SqlDemoTarget.requireIdentity(values[0],values[1],values[2],values[3],values[4],values[5],metadata));
        }
    }
    @Test void sourceCannotAcceptCredentialOverride(){
        var source=SqlDemoTarget.source(env(""));
        assertThrows(SQLException.class,()->source.getConnection("sa","unused"));
        assertTrue(SqlDemoTarget.URL.contains("databaseName=UTEExpress_Demo;"));
        assertFalse(SqlDemoTarget.URL.contains("UTEExpressDB"));assertFalse(SqlDemoTarget.URL.contains("UTEExpress_Test"));
    }
}
