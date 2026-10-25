package com.ute.uteexpress.operations;

import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.AdminUserService;
import com.ute.uteexpress.service.reports.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration(proxyBeanMethods=false)
@Import({OperationsJdbcConfig.class,AdminReportingAccess.class,ReportPolicy.class,AdminReportQueries.class,AdminAuditQueries.class,
    AdminReportService.class,AdminAuditService.class,ReportCsvService.class,AdminUserService.class})
class AdminReportingJdbcConfig { }
