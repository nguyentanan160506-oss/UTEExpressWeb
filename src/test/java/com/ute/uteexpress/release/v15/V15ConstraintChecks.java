package com.ute.uteexpress.release.v15;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import static com.ute.uteexpress.release.v15.V15Sql.*;

final class V15ConstraintChecks {
    private V15ConstraintChecks() { }
    @FunctionalInterface interface SqlWork {void execute(Connection c)throws SQLException;}
    static void run(DataSource source,V15LegacyFixtures.Rows rows)throws SQLException {
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try{for(String scope:List.of("INTRA_PROVINCE","SAME_REGION","NORTH_CENTRAL","CENTRAL_SOUTH","NORTH_SOUTH"))V15LegacyFixtures.rate(c,rows.service(),rows.version(),scope,LocalDateTime.of(2026,1,1,0,0),"30000.00");}
            finally{c.rollback();}
        }
        pass("all-five-scopes-accepted-in-transaction");
        LocalDateTime from=LocalDateTime.of(2026,1,1,0,0);
        String pickup="INSERT INTO dbo.pickup_fee_rules(branch_id,service_id,amount,effective_from,pricing_version_id) OUTPUT INSERTED.id VALUES(?,?,?,?,?)";
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try{insert(c,pickup,null,rows.service(),amount("10000.00"),from,rows.version());c.commit();}
            catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
        }
        try(var c=source.getConnection()){require(scalar(c,"SELECT COUNT_BIG(*) FROM dbo.pickup_fee_rules WHERE branch_id IS NULL AND pricing_version_id=?",rows.version())==1,"Default pickup not committed");}
        pass("global-default-pickup-commit-reconnect");
        reject(source,Set.of(2601,2627),"UX_pickup_rules_version_branch",c->insert(c,pickup,null,rows.service(),amount("11000.00"),from.plusDays(1),rows.version()));pass("duplicate-default-version-unique");
        reject(source,Set.of(547),"FK_pickup_fee_rules_branch",c->insert(c,pickup,Long.MAX_VALUE,rows.service(),amount("10000.00"),from,rows.version()));pass("pickup-branch-fk");
        reject(source,Set.of(547),"FK_pickup_fee_rules_service",c->insert(c,pickup,rows.branch(),Long.MAX_VALUE,amount("10000.00"),from,null));pass("pickup-service-fk");
        reject(source,Set.of(547),"FK_pickup_rules_pricing_version",c->insert(c,pickup,rows.branch(),rows.service(),amount("10000.00"),from,Long.MAX_VALUE));pass("pickup-version-fk");
        reject(source,Set.of(547),"CK_pickup_fee_rules_amount",c->insert(c,pickup,rows.branch(),rows.service(),amount("-1.00"),from.plusDays(1),null));pass("pickup-positive-amount-check");
        for(String table:V15SchemaChecks.TABLES) {
            long id=table.equals("shipments")?rows.shipment():rows.quote();String constraint=table.equals("shipments")?"CK_shipments_nationwide_price":"CK_quotes_nationwide_price";
            BigDecimal shipping=table.equals("shipments")?amount("12345.67"):amount("23456.78");
            var valid=validSnapshot(shipping);
            try(var c=source.getConnection()) {
                c.setAutoCommit(false);
                try {
                    change(c,table,id,valid);
                    try(var p=c.prepareStatement("SELECT base_shipping_fee,extra_weight_fee,priced_base_weight_kg,priced_step_weight_kg,priced_extra_price FROM dbo."+identifier(table)+" WHERE id=?")) {
                        p.setLong(1,id);try(var r=p.executeQuery()){require(r.next()&&r.getBigDecimal(1).equals(amount("10000.00"))&&r.getBigDecimal(2).equals(shipping.subtract(amount("10000.00")))
                            &&r.getBigDecimal(3).equals(amount("1.00"))&&r.getBigDecimal(4).equals(amount("1.00"))&&r.getBigDecimal(5).scale()==2,"DECIMAL roundtrip mismatch");}
                    }
                }finally{c.rollback();}
            }
            pass(table+"-complete-snapshot-decimal-roundtrip");
            reject(source,Set.of(547),constraint,c->change(c,table,id,Map.of("rate_scope","CENTRAL_SOUTH")));
            var bad=new ArrayList<Map<String,Object>>();
            bad.add(with(valid,"base_shipping_fee",amount("-1.00")));bad.add(with(valid,"extra_weight_fee",amount("-1.00")));
            bad.add(with(valid,"extra_weight_fee",amount("0.00")));bad.add(with(valid,"origin_province_code",null));
            bad.add(with(valid,"destination_province_code","X"));bad.add(with(valid,"rate_scope","INTER_PROVINCE"));
            bad.add(with(valid,"priced_base_weight_kg",amount("0.00")));bad.add(with(valid,"priced_step_weight_kg",amount("0.00")));
            bad.add(with(valid,"priced_extra_price",amount("-0.01")));
            for(var values:bad)reject(source,Set.of(547),constraint,c->change(c,table,id,values));
            reject(source,Set.of(8115),null,c->change(c,table,id,with(valid,"base_shipping_fee",amount("10000000000000000.00"))));
            reject(source,Set.of(8115),null,c->change(c,table,id,with(valid,"priced_base_weight_kg",amount("100000000.00"))));
            pass(table+"-invalid-snapshots-and-decimal-overflow-rollback");
        }
        try(var c=source.getConnection()){V15SchemaChecks.legacyNulls(c,rows);}
        pass("legacy-snapshots-remain-null-after-negative-tests");
    }
    static LinkedHashMap<String,Object> validSnapshot(BigDecimal shipping) {
        var m=new LinkedHashMap<String,Object>();m.put("rate_scope","CENTRAL_SOUTH");m.put("origin_province_code","48");m.put("destination_province_code","79");
        m.put("base_shipping_fee",amount("10000.00"));m.put("extra_weight_fee",shipping.subtract(amount("10000.00")));
        m.put("priced_base_weight_kg",amount("1.00"));m.put("priced_step_weight_kg",amount("1.00"));m.put("priced_extra_price",shipping.subtract(amount("10000.00")));return m;
    }
    private static Map<String,Object> with(Map<String,Object> before,String field,Object value){var copy=new LinkedHashMap<>(before);copy.put(field,value);return copy;}
    static void change(Connection c,String table,long id,Map<String,?> fields)throws SQLException {
        require(V15SchemaChecks.TABLES.contains(table)&&V15SchemaChecks.COLUMNS.containsAll(fields.keySet()),"Unexpected snapshot field");
        var setters=new ArrayList<String>();var values=new ArrayList<Object>();
        for(var e:fields.entrySet()){setters.add(identifier(e.getKey())+"=?");values.add(e.getValue());}values.add(id);
        require(update(c,"UPDATE dbo."+identifier(table)+" SET "+String.join(",",setters)+" WHERE id=?",values.toArray())==1,"Snapshot row missing");
    }
    static void reject(DataSource source,Set<Integer> codes,String name,SqlWork work)throws SQLException {
        boolean failed=false;
        try(var c=source.getConnection()) {c.setAutoCommit(false);try{work.execute(c);}catch(SQLException error){if(!expected(error,codes,name))throw error;failed=true;}finally{c.rollback();}}
        require(failed,"Invalid row was accepted");
    }
    private static void pass(String name){System.out.println("PASS "+name);}
}
