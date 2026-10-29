package com.ute.uteexpress.pricing;

import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class ProvinceCatalogTests {
    @Test void officialStableCodesCoverExactly34ProvincesAndAllThreeBusinessZones() {
        var all=ProvinceCatalog.all(); assertEquals(34,all.size());
        assertEquals(Set.of("01","04","08","11","12","14","15","19","20","22","24","25","31","33","37","38","40","42","44","46","48","51","52","56","66","68","75","79","80","82","86","91","92","96"),
            new HashSet<>(all.stream().map(ProvinceCatalog.Province::code).toList()));
        assertEquals(6,all.stream().filter(ProvinceCatalog.Province::city).count());
        assertEquals(15,all.stream().filter(p->p.region()==ShippingRegion.NORTH).count());
        assertEquals(11,all.stream().filter(p->p.region()==ShippingRegion.CENTRAL).count());
        assertEquals(8,all.stream().filter(p->p.region()==ShippingRegion.SOUTH).count());
        assertThrows(UnsupportedOperationException.class,()->all.clear());
    }
    @Test void everyDirectedPairHasExactlyOneSymmetricGroupWithIndependentExpectedCounts() {
        var counts=new EnumMap<RateScopeType,Integer>(RateScopeType.class);int pairs=0;
        for(var a:ProvinceCatalog.all())for(var b:ProvinceCatalog.all()) {
            var actual=ProvinceCatalog.classify(a.code(),b.name());
            assertTrue(RateScopeType.nationwide().contains(actual));
            assertEquals(actual,ProvinceCatalog.classify(b.code(),a.name()));
            assertEquals(a.code().equals(b.code()),actual==RateScopeType.INTRA_PROVINCE);
            counts.merge(actual,1,Integer::sum);pairs++;
        }
        assertEquals(1156,pairs);
        assertEquals(Map.of(RateScopeType.INTRA_PROVINCE,34,RateScopeType.SAME_REGION,376,
            RateScopeType.NORTH_CENTRAL,330,RateScopeType.CENTRAL_SOUTH,176,RateScopeType.NORTH_SOUTH,240),counts);
    }
    @ParameterizedTest @CsvSource({"Hà Nội,Hà Nội,INTRA_PROVINCE","Hà Nội,Hải Phòng,SAME_REGION","Hà Nội,Đà Nẵng,NORTH_CENTRAL","Đà Nẵng,Hồ Chí Minh,CENTRAL_SOUTH","Hồ Chí Minh,Hà Nội,NORTH_SOUTH","Lâm Đồng,Đà Nẵng,SAME_REGION"})
    void explicitExamples(String a,String b,RateScopeType expected){assertEquals(expected,ProvinceCatalog.classify(a,b));}
    @Test void recognizedSpellingAliasesDoNotInventMergedProvinceOrDistrictMappings() {
        assertEquals("79",ProvinceCatalog.require(" TP.HCM ","senderProvince").code());
        assertEquals("48",ProvinceCatalog.require("TP. Đà Nẵng","senderProvince").code());
        assertEquals("01",ProvinceCatalog.require("Thành phố Hà Nội","senderProvince").code());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"Hà Giang","Quảng Nam","Bình Dương","Miền Bắc","HCM","99","Hà Nội/Đà Nẵng","<script>"})
    void unknownOrHistoricalAmbiguousInputsAreRejected(String value){assertThrows(ShipmentCreationException.class,()->ProvinceCatalog.classify(value,"Hà Nội"));}
    @ParameterizedTest @CsvSource({"0.01,20000.00","0.99,20000.00","1.00,20000.00","1.01,25000.00","1.99,25000.00","2.00,25000.00","2.01,30000.00","10.25,70000.00"})
    void weightIsRoundedUpOnlyAboveIncludedFirstKilogram(String weight,String expected) {
        assertEquals(new BigDecimal(expected),ShippingChargeCalculator.calculate(new BigDecimal(weight),BigDecimal.ONE,new BigDecimal("20000"),BigDecimal.ONE,new BigDecimal("5000")).total());
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.001","100000000"})
    void invalidWeightIsRejected(String weight){assertThrows(ShipmentCreationException.class,()->ShippingChargeCalculator.calculate(new BigDecimal(weight),BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.TEN));}
    @Test void negativeInvalidStepsFractionalMoneyAndOverflowAreRejected() {
        assertThrows(ShipmentCreationException.class,()->ShippingChargeCalculator.calculate(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN));
        assertThrows(ShipmentCreationException.class,()->ShippingChargeCalculator.money(new BigDecimal("-0.01")));
        assertThrows(ShipmentCreationException.class,()->ShippingChargeCalculator.money(new BigDecimal("0.001")));
        assertThrows(ShipmentCreationException.class,()->ShippingChargeCalculator.calculate(BigDecimal.TEN,BigDecimal.ONE,new BigDecimal("9999999999999999.99"),BigDecimal.ONE,BigDecimal.TEN));
    }
}
