package com.ute.uteexpress.release.v15;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import static com.ute.uteexpress.release.v15.V15Sql.*;

/** No Flyway instance, no SET/DML/DDL, no freshness assumption for Inspect. */
final class V15ReadOnlyInspection {
    private V15ReadOnlyInspection() { }
    static List<String> inspect(DataSource source)throws SQLException {
        var output=new ArrayList<String>();
        try(var c=source.getConnection()) {
            var id=V15QaTarget.identity(c);
            output.add("PASS inspect-identity machine="+id.machine()+" instance="+id.instance()+" port="+id.port()+" catalog="+id.catalog()+" database="+id.database()+" login="+id.login());
            output.add("PASS inspect-protected-access count="+id.protectedAccess().size()+" all-zero=true");
            long objects=scalar(c,"SELECT COUNT_BIG(*) FROM sys.objects WHERE is_ms_shipped=0");var tables=new LinkedHashMap<String,String>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT s.name,t.name FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id WHERE t.is_ms_shipped=0 ORDER BY s.name,t.name")) {
                while(r.next()){String schema=r.getString(1),name=r.getString(2);tables.put(schema+"."+name,identifier(schema)+"."+identifier(name));}
            }
            output.add("PASS inspect-objects count="+objects+" tables="+tables.size());
            for(var table:tables.entrySet())output.add("PASS inspect-table name="+table.getKey()+" rows="+scalar(c,"SELECT COUNT_BIG(*) FROM "+table.getValue()));
            boolean history=tables.containsKey("dbo.flyway_schema_history");output.add("PASS inspect-history exists="+history);
            if(history)try(var s=c.createStatement();var r=s.executeQuery("SELECT installed_rank,version,checksum,success FROM dbo.flyway_schema_history ORDER BY installed_rank")) {
                while(r.next()) {
                    String version=r.getString(2);require(version==null||version.matches("[0-9]+(?:\\.[0-9]+)*"),"Unexpected history version");
                    int checksum=r.getInt(3);String value=r.wasNull()?"none":Integer.toString(checksum);
                    output.add("PASS inspect-history-row rank="+r.getInt(1)+" version="+(version==null?"none":version)+" checksum="+value+" success="+r.getBoolean(4));
                }
            }
            long columns=scalar(c,"SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id IN (OBJECT_ID(N'dbo.shipments'),OBJECT_ID(N'dbo.shipment_quotes')) AND name IN ('rate_scope','origin_province_code','destination_province_code','base_shipping_fee','extra_weight_fee','priced_base_weight_kg','priced_step_weight_kg','priced_extra_price')");
            output.add("PASS inspect-v15 columns="+columns+" checks="+scalar(c,"SELECT COUNT_BIG(*) FROM sys.check_constraints WHERE name IN ('CK_quotes_nationwide_price','CK_shipments_nationwide_price')"));
            if(tables.containsKey("dbo.pickup_fee_rules"))output.add("PASS inspect-default-pickup rows="+scalar(c,"SELECT COUNT_BIG(*) FROM dbo.pickup_fee_rules WHERE branch_id IS NULL"));
        }
        output.add("RESULT INSPECT SELECT-only-no-migrations-no-fixtures");return List.copyOf(output);
    }
}
