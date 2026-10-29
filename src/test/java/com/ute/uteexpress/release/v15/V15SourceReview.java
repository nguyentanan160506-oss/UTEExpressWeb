package com.ute.uteexpress.release.v15;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.database.sqlserver.SQLServerParser;

final class V15SourceReview {
    static final int CHECKSUM=347627752;
    static final String NAME="V15__add_nationwide_shipping_pricing.sql";
    private V15SourceReview() { }
    static String source()throws Exception {
        try(var in=V15SourceReview.class.getResourceAsStream("/db/migration/"+NAME)) {
            if(in==null)throw new IllegalStateException("V15 source missing");return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    static void requireReviewed()throws Exception {
        String sql=source();V15Sql.require(ChecksumCalculator.calculate(new StringResource(sql))==CHECKSUM,"V15 changed; review source before writing");
        var batches=new ArrayList<String>();var parser=new SQLServerParser(Flyway.configure().placeholderReplacement(false),new ParsingContext());
        try(var statements=parser.parse(new StringResource(sql))){while(statements.hasNext()){
            var statement=statements.next();V15Sql.require(statement.canExecuteInTransaction(),"Nontransactional V15 batch");batches.add(statement.getSql());}}
        V15Sql.require(batches.size()==2&&batches.getFirst().contains("ADD rate_scope VARCHAR(30) NULL")
            &&!batches.getFirst().contains("CK_quotes_nationwide_price")&&batches.getLast().contains("CK_quotes_nationwide_price"),"Unexpected V15 batch boundaries");
    }
}
