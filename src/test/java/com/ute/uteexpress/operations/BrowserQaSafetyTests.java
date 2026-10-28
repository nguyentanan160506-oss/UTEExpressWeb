package com.ute.uteexpress.operations;

import java.security.SecureRandom;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrowserQaSafetyTests {
    @Test void runtimeCredentialAndOtpAreRejectedBeforeReportIsWritten(){
        String password=UUID.randomUUID().toString();
        String code=String.format("%06d",new SecureRandom().nextInt(1_000_000));
        var secrets=List.of(password,code);
        assertDoesNotThrow(()->BrowserQaTool.requireRedactedReport("{\"status\":\"PASS\"}",secrets));
        for(String secret:secrets){
            var error=assertThrows(IllegalStateException.class,
                ()->BrowserQaTool.requireRedactedReport("{\"detail\":\""+secret+"\"}",secrets));
            assertFalse(error.getMessage().contains(secret),"Diagnostic must also redact runtime secrets");
        }
    }
}
