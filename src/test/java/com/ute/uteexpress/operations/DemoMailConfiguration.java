package com.ute.uteexpress.operations;

import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.mail.javamail.*;

@TestConfiguration(proxyBeanMethods=false)
@Profile({"demo-local","demo-sql"})
@ConditionalOnProperty(name="uteexpress.demo.test-mail.enabled",havingValue="true")
public class DemoMailConfiguration {
    @Bean(destroyMethod="close") DemoSmtpServer demoSmtpServer(DataSource source,org.springframework.core.env.Environment environment)throws Exception {
        if(environment.matchesProfiles("demo-sql")) {
            try(var c=source.getConnection()){com.ute.uteexpress.demo.SqlDemoTarget.verify(c);}
        }else DemoSmtpServer.requireDisposableH2(source);
        return new DemoSmtpServer();
    }
    @Bean JavaMailSender demoJavaMailSender(DemoSmtpServer sink) {
        var sender=new JavaMailSenderImpl();sender.setHost("127.0.0.1");sender.setPort(sink.port());
        sender.setDefaultEncoding("UTF-8");
        sender.getJavaMailProperties().setProperty("mail.smtp.auth","false");
        sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout","3000");
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout","3000");
        sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout","3000");
        return sender;
    }
}
