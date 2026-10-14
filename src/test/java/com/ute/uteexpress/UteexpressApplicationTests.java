package com.ute.uteexpress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

/** Opt-in, read-only schema validation using Windows integrated authentication. */
@EnabledIfSystemProperty(named = "uteexpress.sqlserver.validate", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
	"spring.flyway.enabled=false",
	"spring.sql.init.mode=never",
	"spring.jpa.hibernate.ddl-auto=validate",
	"spring.datasource.url=jdbc:sqlserver://127.0.0.1:1433;databaseName=UTEExpressDB;encrypt=true;trustServerCertificate=true;integratedSecurity=true",
	"spring.datasource.username=",
	"spring.datasource.password=",
	"spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
})
class UteexpressApplicationTests {

	@Test
	void contextLoads() {
	}

}
