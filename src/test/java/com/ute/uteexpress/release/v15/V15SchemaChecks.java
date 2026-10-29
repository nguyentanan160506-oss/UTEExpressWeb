package com.ute.uteexpress.release.v15;

import java.sql.*;
import java.util.*;
import static com.ute.uteexpress.release.v15.V15Sql.*;

/** SQL Server catalog checks, always on a connection already identity-checked by GuardedSource. */
final class V15SchemaChecks {
    static final List<String> TABLES=List.of("shipment_quotes","shipments");
    static final List<String> COLUMNS=List.of("rate_scope","origin_province_code","destination_province_code","base_shipping_fee","extra_weight_fee","priced_base_weight_kg","priced_step_weight_kg","priced_extra_price");
    private V15SchemaChecks() { }
    static void baseline(Connection c)throws SQLException {
        require(scalar(c,"SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id IN (OBJECT_ID(N'dbo.shipments'),OBJECT_ID(N'dbo.shipment_quotes')) AND name IN ('rate_scope','origin_province_code','destination_province_code','base_shipping_fee','extra_weight_fee','priced_base_weight_kg','priced_step_weight_kg','priced_extra_price')")==0,"V15 columns already present");
        require(scalar(c,"SELECT COUNT_BIG(*) FROM sys.check_constraints WHERE name IN ('CK_quotes_nationwide_price','CK_shipments_nationwide_price')")==0,"V15 checks already present");
        require(scalar(c,"SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.pickup_fee_rules') AND name='branch_id' AND is_nullable=0")==1,"Unexpected baseline pickup nullability");
        scopes(c,Set.of("INTRA_PROVINCE","INTER_PROVINCE","INTER_REGION"));pickupIndexes(c);pickupForeignKeys(c);v14(c);
    }
    static void upgraded(Connection c)throws SQLException {
        for(String table:TABLES) {
            for(String column:COLUMNS) {
                String type=column.equals("rate_scope")||column.endsWith("province_code")?"varchar":"decimal";
                int length=column.equals("rate_scope")?30:column.endsWith("province_code")?2:0;
                int precision=column.endsWith("_kg")?10:18;
                String sql="SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id=OBJECT_ID(?) AND name=? AND TYPE_NAME(user_type_id)=? AND is_nullable=1"
                    +(type.equals("varchar")?" AND max_length=?":" AND precision=? AND scale=2");
                require(scalar(c,sql,"dbo."+table,column,type,type.equals("varchar")?length:precision)==1,"Snapshot column metadata mismatch");
            }
        }
        require(scalar(c,"SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.pickup_fee_rules') AND name='branch_id' AND TYPE_NAME(user_type_id)='bigint' AND is_nullable=1")==1,"Default pickup nullable column missing");
        for(String check:List.of("CK_quotes_nationwide_price","CK_shipments_nationwide_price"))
            require(scalar(c,"SELECT COUNT_BIG(*) FROM sys.check_constraints WHERE name=? AND is_disabled=0 AND is_not_trusted=0",check)==1,"Snapshot CHECK disabled or untrusted");
        scopes(c,Set.of("INTRA_PROVINCE","INTER_PROVINCE","INTER_REGION","SAME_REGION","NORTH_CENTRAL","CENTRAL_SOUTH","NORTH_SOUTH"));
        pickupIndexes(c);pickupForeignKeys(c);v14(c);
    }
    private static void scopes(Connection c,Set<String> expected)throws SQLException {
        try(var p=c.prepareStatement("SELECT definition FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.shipping_rate_rules') AND name='CK_rate_rules_scope' AND is_disabled=0 AND is_not_trusted=0");var r=p.executeQuery()) {
            require(r.next(),"Rate scope CHECK missing/untrusted");String definition=r.getString(1);require(definition!=null,"Rate scope definition missing");
            var values=new HashSet<String>();var matcher=java.util.regex.Pattern.compile("'([^']+)'").matcher(definition);
            while(matcher.find())values.add(matcher.group(1));require(values.equals(expected),"Unexpected allowed rate scopes");
        }
    }
    static Map<String,String> pickupSignature(Connection c)throws SQLException {
        var result=new TreeMap<String,String>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT i.name,i.is_unique,i.has_filter,i.filter_definition,i.is_disabled,i.ignore_dup_key,c.name,ic.key_ordinal FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE i.object_id=OBJECT_ID(N'dbo.pickup_fee_rules') AND i.name IN ('IX_pickup_fee_rules_lookup','IX_pickup_rules_version','UX_pickup_legacy_start','UX_pickup_rules_version_branch') AND ic.key_ordinal>0 ORDER BY i.name,ic.key_ordinal")) {
            while(r.next()) {
                String name=r.getString(1);require(!r.getBoolean(5)&&!r.getBoolean(6),"Pickup index disabled or IGNORE_DUP_KEY enabled");
                String prefix=r.getBoolean(2)+"/"+r.getBoolean(3)+"/"+normalize(r.getString(4))+"/";
                String old=result.get(name);result.put(name,old==null?prefix+r.getString(7):old+","+r.getString(7));
            }
        }
        return Collections.unmodifiableMap(result);
    }
    private static void pickupIndexes(Connection c)throws SQLException {
        var expected=Map.of("IX_pickup_fee_rules_lookup","false/false//branch_id,service_id,is_active,effective_from,effective_to",
            "IX_pickup_rules_version","false/false//pricing_version_id,branch_id",
            "UX_pickup_legacy_start","true/true/PRICING_VERSION_IDISNULL/branch_id,service_id,effective_from",
            "UX_pickup_rules_version_branch","true/true/PRICING_VERSION_IDISNOTNULL/pricing_version_id,branch_id");
        require(pickupSignature(c).equals(expected),"Pickup index keys/filter/unique mismatch");
        require(scalar(c,"SELECT COUNT_BIG(*) FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.shipping_rate_rules') AND name='UX_rate_rules_version_scope' AND is_unique=1 AND has_filter=1 AND is_disabled=0 AND ignore_dup_key=0")==1,"Versioned rate uniqueness lost");
    }
    private static void pickupForeignKeys(Connection c)throws SQLException {
        var expected=Map.of("FK_pickup_fee_rules_branch","branch_id->dbo.branches.id","FK_pickup_fee_rules_service","service_id->dbo.shipping_services.id","FK_pickup_rules_pricing_version","pricing_version_id->dbo.pricing_versions.id");
        var actual=new TreeMap<String,String>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT fk.name,pc.name,rs.name,rt.name,rc.name,fk.is_disabled,fk.is_not_trusted,fk.delete_referential_action,fk.update_referential_action FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fc ON fc.constraint_object_id=fk.object_id JOIN sys.columns pc ON pc.object_id=fc.parent_object_id AND pc.column_id=fc.parent_column_id JOIN sys.tables rt ON rt.object_id=fc.referenced_object_id JOIN sys.schemas rs ON rs.schema_id=rt.schema_id JOIN sys.columns rc ON rc.object_id=fc.referenced_object_id AND rc.column_id=fc.referenced_column_id WHERE fk.parent_object_id=OBJECT_ID(N'dbo.pickup_fee_rules')")) {
            while(r.next()){require(!r.getBoolean(6)&&!r.getBoolean(7)&&r.getInt(8)==0&&r.getInt(9)==0,"Pickup FK disabled/untrusted/cascading");actual.put(r.getString(1),r.getString(2)+"->"+r.getString(3)+"."+r.getString(4)+"."+r.getString(5));}
        }
        require(actual.equals(expected),"Pickup FK changed");
    }
    private static void v14(Connection c)throws SQLException {
        require(scalar(c,"SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.shipment_intakes') AND ((name='system_receipt_code' AND TYPE_NAME(user_type_id)='varchar' AND max_length=45 AND is_nullable=1) OR (name='request_version' AND TYPE_NAME(user_type_id)='bigint' AND is_nullable=1))")==2,"V14 receipt columns missing");
        require(scalar(c,"SELECT COUNT_BIG(*) FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name='UX_intakes_system_receipt_code' AND is_unique=1 AND has_filter=1 AND is_disabled=0")==1,"V14 receipt uniqueness missing");
        require(scalar(c,"SELECT COUNT_BIG(*) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name='CK_intakes_system_receipt' AND is_disabled=0 AND is_not_trusted=0")==1,"V14 receipt CHECK missing");
    }
    static String normalize(String value){return value==null?"":value.replaceAll("[\\[\\]()\\s]","").toUpperCase(Locale.ROOT);}
    static void legacyNulls(Connection c,V15LegacyFixtures.Rows rows)throws SQLException {
        String nulls=String.join(" AND ",COLUMNS.stream().map(n->n+" IS NULL").toList());
        require(scalar(c,"SELECT COUNT_BIG(*) FROM dbo.shipments WHERE id=? AND "+nulls,rows.shipment())==1,"Legacy shipment snapshot inferred");
        require(scalar(c,"SELECT COUNT_BIG(*) FROM dbo.shipment_quotes WHERE id=? AND "+nulls,rows.quote())==1,"Legacy quote snapshot inferred");
    }
}
