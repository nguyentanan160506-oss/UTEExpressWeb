package com.ute.uteexpress.finaldemo;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinalDemoBrowserSafetyTests {
    boolean allowed(String method,String path){return FinalDemoBrowserReadTool.allowedRequest(method,URI.create(path));}
    @Test void permitsOnlyLocalReadAndExplicitNonPersistingOrAuthPosts(){
        for(String method:List.of("GET","HEAD"))assertTrue(allowed(method,"http://127.0.0.1:18085/user/shipments"));
        for(String path:List.of("/login","/logout","/quote","/quote/branches","/user/shipments/branches"))assertTrue(allowed("POST","http://127.0.0.1:18085"+path));
    }
    @Test void rejectsBusinessMutationsAndOtherOrigins(){
        for(String path:List.of("/user/shipments/quotes","/user/shipments","/manager/shipments/1/confirm","/admin/users/1/lock","/notifications/read","/register"))assertFalse(allowed("POST","http://127.0.0.1:18085"+path));
        for(String method:List.of("PUT","PATCH","DELETE"))assertFalse(allowed(method,"http://127.0.0.1:18085/login"));
        for(String url:List.of("http://127.0.0.1:8083/","http://127.0.0.1:18084/","https://example.test/","http://127.0.0.1:18085.evil.test/"))assertFalse(allowed("GET",url));
    }
}
