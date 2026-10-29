package com.ute.uteexpress.finaldemo;

import com.ute.uteexpress.enums.BranchType;
import com.ute.uteexpress.service.ProvinceCatalog;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Verified geography; facility names and detailed addresses are explicitly simulated. */
public final class FinalDemoNetwork {
    private FinalDemoNetwork() { }
    public record Node(String code, BranchType type, String provinceCode, String province,
            String legacyArea, String wardCode, String ward, String hub, String name, String address) { }
    public static List<Node> nodes() {
        try(var in=FinalDemoNetwork.class.getResourceAsStream("/final-demo/network.psv")) {
            if(in==null)throw new IllegalStateException("Network fixture missing");
            var nodes=new ArrayList<Node>();
            for(String line:new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)).lines().skip(1).toList()) {
                var c=line.split("\\|",-1);
                if(c.length!=10)throw new IllegalStateException("Malformed network fixture");
                if(!ProvinceCatalog.require(c[2],"province").name().equals(c[3])||!c[5].matches("[0-9]{5}"))throw new IllegalStateException("Invalid geography");
                nodes.add(new Node(c[0],BranchType.valueOf(c[1]),c[2],c[3],c[4],c[5],c[6],c[7],c[8],c[9]));
            }
            if(nodes.size()!=42||nodes.stream().map(Node::code).distinct().count()!=42
                    ||nodes.stream().filter(n->n.type()==BranchType.LOCAL_BRANCH).count()!=35
                    ||nodes.stream().map(Node::provinceCode).distinct().count()!=34)throw new IllegalStateException("Network cardinality mismatch");
            for(var n:nodes)if(n.type()==BranchType.LOCAL_BRANCH&&nodes.stream().noneMatch(h->h.code().equals(n.hub())&&h.type()==BranchType.CENTRAL_HUB))throw new IllegalStateException("Missing parent hub");
            return List.copyOf(nodes);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
}
