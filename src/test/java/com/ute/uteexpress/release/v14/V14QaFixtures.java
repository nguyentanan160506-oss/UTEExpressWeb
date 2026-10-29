package com.ute.uteexpress.release.v14;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Minimal synthetic FK fixtures, NOT an application demo seed. Only callable after the fresh target guard. */
final class V14QaFixtures {
    private V14QaFixtures() { }
    record Rows(long actor,long branch,long employee,long service,List<Long> shipments) { }
    static long insert(Connection c,String sql,Object... values)throws SQLException {
        try(var p=c.prepareStatement(sql)){
            for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);
            try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Fixture insert returned no key");return r.getLong(1);}
        }
    }
    static Rows create(DataSource source)throws SQLException {
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try {
                String hash=new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
                long actor=insert(c,"INSERT INTO dbo.users(username,email,password_hash,full_name,phone_number,status) OUTPUT INSERTED.id VALUES(?,?,?,?,?,'LOCKED')",
                    "v14qa_actor","v14qa_actor@example.test",hash,"Synthetic V14 QA actor","0000000000");
                long branch=insert(c,"INSERT INTO dbo.branches(code,name,branch_type,phone,province,district,ward,detail_address) OUTPUT INSERTED.id VALUES(?,?,'LOCAL_BRANCH',?,?,?,?,?)",
                    "V14QA-BRANCH","Synthetic QA branch","0000000000","QA","QA","QA","Synthetic location");
                long employee=insert(c,"INSERT INTO dbo.employees(branch_id,employee_code,full_name,phone_number,position,user_id) OUTPUT INSERTED.id VALUES(?,?,?,?,'DISPATCHER',?)",
                    branch,"V14QA-EMP","Synthetic QA employee","0000000000",actor);
                long service=insert(c,"INSERT INTO dbo.shipping_services(code,name,estimated_delivery_hours) OUTPUT INSERTED.id VALUES(?,?,24)","V14QA-SERVICE","Synthetic QA service");
                var shipments=new ArrayList<Long>();
                for(int i=0;i<4;i++) {
                    String sql="INSERT INTO dbo.shipments(tracking_number,sender_id,sender_name,sender_phone,sender_province,sender_district,sender_ward,sender_address,"
                        +"receiver_name,receiver_phone,receiver_province,receiver_district,receiver_ward,receiver_address,origin_branch_id,destination_branch_id,"
                        +"current_branch_id,service_id,package_description,weight_kg,shipping_fee,total_fee,status,shipment_method) OUTPUT INSERTED.id "
                        +"VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,1,1,?,'BRANCH_DROPOFF')";
                    long shipment=insert(c,sql,"UTE"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT),actor,
                        "QA sender","0000000000","QA","QA","QA","Synthetic sender location","QA receiver","0000000000","QA","QA","QA","Synthetic receiver location",
                        branch,branch,i<2?branch:null,service,"Synthetic QA parcel",i<2?"AT_ORIGIN_BRANCH":"CONFIRMED");
                    shipments.add(shipment);
                    if(i<2)insert(c,"INSERT INTO dbo.shipment_intakes(shipment_id,branch_id,received_by,employee_id,shipment_method,receipt_reference) OUTPUT INSERTED.id VALUES(?,?,?,?,'BRANCH_DROPOFF',?)",
                        shipment,branch,actor,employee,"V14QA-LEGACY-DUPLICATE");
                }
                c.commit();return new Rows(actor,branch,employee,service,List.copyOf(shipments));
            }catch(SQLException|RuntimeException error){c.rollback();throw error;}
        }
    }
    static long intake(Connection c,Rows rows,long shipment,String reference,String systemCode,Long requestVersion)throws SQLException {
        return insert(c,"INSERT INTO dbo.shipment_intakes(shipment_id,branch_id,received_by,employee_id,shipment_method,receipt_reference,system_receipt_code,request_version) "
            +"OUTPUT INSERTED.id VALUES(?,?,?,?,'BRANCH_DROPOFF',?,?,?)",shipment,rows.branch(),rows.actor(),rows.employee(),reference,systemCode,requestVersion);
    }
    static List<List<String>> legacy(Connection c)throws SQLException {
        var rows=new ArrayList<List<String>>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT id,shipment_id,branch_id,received_by,employee_id,shipment_method,receipt_reference,notes,received_at FROM dbo.shipment_intakes WHERE receipt_reference=N'V14QA-LEGACY-DUPLICATE' ORDER BY id")) {
            while(r.next()){
                var row=new ArrayList<String>();for(int i=1;i<=9;i++)row.add(Objects.toString(r.getObject(i),"<NULL>"));rows.add(List.copyOf(row));
            }
        }
        return List.copyOf(rows);
    }
}
