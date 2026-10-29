package com.ute.uteexpress.release.v14;

import java.sql.SQLException;
import java.util.*;

/** Exception structure only. Never render messages, settings, bind values, URLs or credential objects. */
final class V14QaDiagnostics {
    private V14QaDiagnostics() { }
    static List<String> lines(String stage,Throwable error) {
        var causes=new ArrayList<Throwable>();var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable t=error;t!=null&&causes.size()<8&&seen.add(t);t=t.getCause())causes.add(t);
        SQLException sql=causes.stream().filter(SQLException.class::isInstance).map(SQLException.class::cast).findFirst().orElse(null);
        var lines=new ArrayList<String>();
        lines.add("FAIL stage="+token(stage,"[a-z0-9-]+")+" type="+token(error.getClass().getSimpleName(),"[A-Za-z0-9]+")
            +(sql==null?"":" SQLState="+sqlState(sql)+" code="+sql.getErrorCode()));
        for(int i=0;i<causes.size();i++) {
            Throwable cause=causes.get(i);lines.add("DIAG cause="+i+" type="+token(cause.getClass().getName(),"[A-Za-z0-9_.$]+"));
            int count=0;
            for(var frame:cause.getStackTrace()) {
                if(count++==8)break;
                lines.add("DIAG cause="+i+" frame="+(count-1)+" class="+token(frame.getClassName(),"[A-Za-z0-9_.$]+")
                    +" method="+token(frame.getMethodName(),"[A-Za-z0-9_$<>]+")
                    +" file="+token(frame.getFileName(),"[A-Za-z0-9_.$-]+")+" line="+frame.getLineNumber());
            }
        }
        if(sql!=null) {
            var sqlSeen=Collections.newSetFromMap(new IdentityHashMap<SQLException,Boolean>());int n=0;
            for(var next=sql;next!=null&&n<8&&sqlSeen.add(next);next=next.getNextException(),n++) {
                lines.add("DIAG sql="+n+" type="+token(next.getClass().getName(),"[A-Za-z0-9_.$]+")+" SQLState="+sqlState(next)+" code="+next.getErrorCode());
            }
        }
        return List.copyOf(lines);
    }
    private static String sqlState(SQLException sql){String state=sql.getSQLState();return state!=null&&state.matches("[A-Z0-9]{5}")?state:"UNKNOWN";}
    private static String token(String value,String pattern){return value!=null&&value.matches(pattern)?value:"unknown";}
}
