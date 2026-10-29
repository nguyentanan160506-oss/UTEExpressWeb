package com.ute.uteexpress.release.v15;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static com.ute.uteexpress.release.v15.V15Sql.*;

/** Synthetic pre-upgrade rows only. Never seed a protected database or print row values/tokens/hash. */
final class V15LegacyFixtures {
    private V15LegacyFixtures() { }
    record Rows(long actor,long hub,long branch,long service,long version,long interProvince,long interRegion,long pickup,long shipment,long quote,long payment) {
        Map<String,Long> protectedRows(){var rows=new LinkedHashMap<String,Long>();
            rows.put("branches",branch);rows.put("shipping_services",service);rows.put("pricing_versions",version);
            rows.put("shipping_rate_rules",interProvince);rows.put("pickup_fee_rules",pickup);rows.put("shipments",shipment);
            rows.put("shipment_quotes",quote);rows.put("shipment_payments",payment);return rows;}
    }
    static Rows create(DataSource source)throws SQLException {
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try {
                String hash=new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
                long actor=insert(c,"INSERT INTO dbo.users(username,email,password_hash,full_name,phone_number,status) OUTPUT INSERTED.id VALUES(?,?,?,?,?,'LOCKED')",
                    "v15qa_legacy","v15qa_legacy@example.test",hash,"Synthetic V15 legacy actor","0000000000");
                long hub=insert(c,"INSERT INTO dbo.branches(code,name,branch_type,phone,province,district,ward,detail_address) OUTPUT INSERTED.id VALUES(?,?,'CENTRAL_HUB',?,?,?,?,?)",
                    "V15QA-LEGACY-HUB","Synthetic legacy hub","0000000000","Quảng Nam","QA","QA","Synthetic legacy location");
                long branch=insert(c,"INSERT INTO dbo.branches(code,name,branch_type,parent_hub_id,phone,province,district,ward,detail_address) OUTPUT INSERTED.id VALUES(?,?,'LOCAL_BRANCH',?,?,?,?,?,?)",
                    "V15QA-LEGACY-BRANCH","Synthetic legacy branch",hub,"0000000000","Quảng Nam","QA","QA","Synthetic legacy location");
                long service=insert(c,"INSERT INTO dbo.shipping_services(code,name,estimated_delivery_hours) OUTPUT INSERTED.id VALUES(?,?,24)","V15QA-LEGACY","Synthetic legacy catalog");
                LocalDateTime start=LocalDateTime.of(2026,1,1,0,0);
                long version=insert(c,"INSERT INTO dbo.pricing_versions(service_id,code,status,valid_from,created_by,updated_by,published_by,published_at) OUTPUT INSERTED.id VALUES(?,?,'PUBLISHED',?,?,?,?,?)",
                    service,"V15QA-LEGACY",start,actor,actor,actor,start);
                long inter=rate(c,service,version,"INTER_PROVINCE",start,"12345.67"),region=rate(c,service,version,"INTER_REGION",start,"23456.78");
                long pickup=insert(c,"INSERT INTO dbo.pickup_fee_rules(branch_id,service_id,amount,effective_from,pricing_version_id) OUTPUT INSERTED.id VALUES(?,?,?,?,?)",branch,service,amount("7500.00"),start,version);
                long shipment=insert(c,"INSERT INTO dbo.shipments(tracking_number,sender_id,sender_name,sender_phone,sender_province,sender_district,sender_ward,sender_address,receiver_name,receiver_phone,receiver_province,receiver_district,receiver_ward,receiver_address,origin_branch_id,destination_branch_id,service_id,package_description,weight_kg,shipping_fee,total_fee,payment_status) OUTPUT INSERTED.id VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PAID')",
                    "UTE"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT),actor,"Synthetic sender","0000000000","Quảng Nam","QA","QA","Synthetic old sender address",
                    "Synthetic recipient","0000000001","Bình Dương","QA","QA","Synthetic old recipient address",branch,branch,service,"Synthetic legacy parcel",amount("1.25"),amount("12345.67"),amount("12345.67"));
                LocalDateTime created=LocalDateTime.of(2026,1,2,0,0),expires=created.plusHours(1);
                long quote=insert(c,"INSERT INTO dbo.shipment_quotes(requester_id,origin_branch_id,destination_branch_id,origin_hub_id,destination_hub_id,service_id,shipping_rate_rule_id,token,shipment_method,sender_name,sender_phone,sender_province,sender_district,sender_ward,sender_address,receiver_name,receiver_phone,receiver_province,receiver_district,receiver_ward,receiver_address,package_description,weight_kg,cod_amount,declared_value,payment_payer,shipping_fee,pickup_fee,insurance_fee,total_fee,created_at,expires_at) OUTPUT INSERTED.id VALUES(?,?,?,?,?,?,?,?,'BRANCH_DROPOFF',?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,0,'SENDER',?,0,0,?,?,?)",
                    actor,branch,branch,hub,hub,service,region,UUID.randomUUID().toString().replace("-",""),"Synthetic sender","0000000000","Quảng Nam","QA","QA","Synthetic quote sender address",
                    "Synthetic recipient","0000000001","Bình Dương","QA","QA","Synthetic quote recipient address","Synthetic legacy quote",amount("1.25"),amount("23456.78"),amount("23456.78"),created,expires);
                long payment=insert(c,"INSERT INTO dbo.shipment_payments(shipment_id,payment_code,payment_type,payment_method,amount,status,transaction_reference,paid_at) OUTPUT INSERTED.id VALUES(?,?,'SHIPPING_FEE','BANK_TRANSFER',?,'SUCCESS',?,?)",
                    shipment,"V15QA-LEGACY-PAYMENT",amount("12345.67"),"SYNTHETIC-QA-REFERENCE",created);
                c.commit();return new Rows(actor,hub,branch,service,version,inter,region,pickup,shipment,quote,payment);
            }catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
        }
    }
    static long rate(Connection c,long service,long version,String scope,LocalDateTime from,String base)throws SQLException {
        return insert(c,"INSERT INTO dbo.shipping_rate_rules(service_id,pricing_version_id,scope_type,base_weight_kg,base_price,extra_weight_step_kg,extra_price_per_step,effective_from) OUTPUT INSERTED.id VALUES(?,?,?,1,?,1,5000,?)",service,version,scope,amount(base),from);
    }
    static final class Snapshot {
        private final List<Entry> entries;
        private Snapshot(List<Entry> entries){this.entries=List.copyOf(entries);}
        @Override public String toString(){return "Legacy QA snapshot [redacted]";}
        void verify(DataSource source)throws Exception {
            try(var c=source.getConnection()){for(var e:entries)require(e.hash().equals(fingerprint(c,e.table(),e.id(),e.columns())),"Legacy row changed: "+e.table());}
        }
    }
    private record Entry(String table,long id,List<String> columns,String hash) { }
    static Snapshot snapshot(DataSource source,Rows rows)throws Exception {
        var entries=new ArrayList<Entry>();
        try(var c=source.getConnection()) {
            var ids=new LinkedHashMap<>(rows.protectedRows());
            for(var entry:ids.entrySet())entries.add(capture(c,entry.getKey(),entry.getValue()));
            entries.add(capture(c,"shipping_rate_rules",rows.interRegion()));entries.add(capture(c,"branches",rows.hub()));
        }
        return new Snapshot(entries);
    }
    private static Entry capture(Connection c,String table,long id)throws Exception {
        var columns=new ArrayList<String>();
        try(var p=c.prepareStatement("SELECT * FROM dbo."+identifier(table)+" WHERE id=?")) {
            p.setLong(1,id);try(var r=p.executeQuery()){require(r.next(),"Legacy row missing");for(int i=1;i<=r.getMetaData().getColumnCount();i++)columns.add(r.getMetaData().getColumnName(i));}
        }
        return new Entry(table,id,List.copyOf(columns),fingerprint(c,table,id,columns));
    }
    private static String fingerprint(Connection c,String table,long id,List<String> columns)throws Exception {
        String select=String.join(",",columns.stream().map(name->{try{return identifier(name);}catch(SQLException invalid){throw new IllegalStateException(invalid);}}).toList());
        var digest=MessageDigest.getInstance("SHA-256");
        try(var p=c.prepareStatement("SELECT "+select+" FROM dbo."+identifier(table)+" WHERE id=?")) {
            p.setLong(1,id);try(var r=p.executeQuery()) {
                require(r.next(),"Legacy row missing");
                for(int i=1;i<=columns.size();i++){String value=Objects.toString(r.getObject(i),"<NULL>");byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}
                require(!r.next(),"Legacy ID is not unique");
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
