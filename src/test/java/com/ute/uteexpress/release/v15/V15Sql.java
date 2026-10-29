package com.ute.uteexpress.release.v15;

import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

/** Prepared values and bounded identifiers. No helper here logs parameters or SQL exception messages. */
final class V15Sql {
    private V15Sql() { }
    static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    static long scalar(Connection c,String sql,Object... params)throws SQLException {
        try(var p=c.prepareStatement(sql)){bind(p,params);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Missing scalar");return r.getLong(1);}}
    }
    static int update(Connection c,String sql,Object... params)throws SQLException {
        try(var p=c.prepareStatement(sql)){bind(p,params);return p.executeUpdate();}
    }
    static long insert(Connection c,String sql,Object... params)throws SQLException {
        try(var p=c.prepareStatement(sql)){bind(p,params);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Missing generated ID");return r.getLong(1);}}
    }
    static void bind(PreparedStatement statement,Object... values)throws SQLException{for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);}
    static String identifier(String value)throws SQLException {
        if(value==null||!value.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))throw new SQLException("Unexpected identifier","08004");return "["+value+"]";
    }
    static boolean expected(Throwable error,Set<Integer> codes,String constraint) {
        var visited=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable t=error;t!=null&&visited.add(t);t=t.getCause())if(t instanceof SQLException sql) {
            var seen=Collections.newSetFromMap(new IdentityHashMap<SQLException,Boolean>());
            for(var s=sql;s!=null&&seen.add(s);s=s.getNextException())if(codes.contains(s.getErrorCode())
                    &&(constraint==null||Objects.toString(s.getMessage(),"").contains(constraint)))return true;
        }
        return false;
    }
    static BigDecimal amount(String value){return new BigDecimal(value);}
}
