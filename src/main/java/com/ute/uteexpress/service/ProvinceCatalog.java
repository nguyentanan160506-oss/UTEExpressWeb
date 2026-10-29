package com.ute.uteexpress.service;

import com.ute.uteexpress.enums.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/** Codes/names: Decision 19/2025/QD-TTg, Appendix I. Zones are our explicit business policy.
 * Does not map merged provinces, rewrite saved addresses, or imply service availability. */
public final class ProvinceCatalog {
    public record Province(String code, String name, ShippingRegion region, boolean city) {
        public String getLabel() { return (city ? "Thành phố " : "Tỉnh ") + name; }
    }
    private static final List<Province> ALL = load();
    private static final Map<String, Province> LOOKUP = lookup();
    private ProvinceCatalog() { }
    public static List<Province> all() { return ALL; }
    public static Province require(String value, String field) {
        Province province = value == null ? null : LOOKUP.get(normalize(value));
        if (province == null) {
            throw new ShipmentCreationException(field, "Vui lòng chọn tỉnh/thành trong danh mục 34 tỉnh/thành hiện hành; không tự quy đổi địa chỉ cũ.");
        }
        return province;
    }
    public static RateScopeType classify(String origin, String destination) {
        var a = require(origin, "senderProvince"); var b = require(destination, "receiverProvince");
        if (a.code().equals(b.code())) { return RateScopeType.INTRA_PROVINCE; }
        if (a.region() == b.region()) { return RateScopeType.SAME_REGION; }
        var pair = EnumSet.of(a.region(), b.region());
        if (pair.contains(ShippingRegion.NORTH) && pair.contains(ShippingRegion.CENTRAL)) { return RateScopeType.NORTH_CENTRAL; }
        if (pair.contains(ShippingRegion.CENTRAL) && pair.contains(ShippingRegion.SOUTH)) { return RateScopeType.CENTRAL_SOUTH; }
        return RateScopeType.NORTH_SOUTH;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value.strip().replaceAll("\\s+", " "), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
    private static Map<String, Province> lookup() {
        var map = new HashMap<String, Province>();
        for (var p : ALL) {
            for (var alias : List.of(p.code(), p.name(), p.getLabel())) { put(map, alias, p); }
            if (p.city()) { for (var prefix : List.of("TP. ", "TP ", "TP.")) { put(map, prefix + p.name(), p); } }
        }
        put(map, "TP.HCM", requireCode("79")); put(map, "TP HCM", requireCode("79"));
        return Map.copyOf(map);
    }
    private static Province requireCode(String code) { return ALL.stream().filter(p -> p.code().equals(code)).findFirst().orElseThrow(); }
    private static void put(Map<String, Province> map, String key, Province value) {
        var old = map.putIfAbsent(normalize(key), value);
        if (old != null && !old.equals(value)) { throw new IllegalStateException("Ambiguous province catalog"); }
    }
    private static List<Province> load() {
        try (var input = ProvinceCatalog.class.getResourceAsStream("/catalog/vietnam-provinces-2025.csv")) {
            if (input == null) { throw new IllegalStateException("Province catalog missing"); }
            var lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines().skip(1).toList();
            var result = new ArrayList<Province>();
            for (String line : lines) {
                String[] c = line.split(",", -1);
                if (c.length != 4 || !c[0].matches("[0-9]{2}") || c[1].isBlank()
                        || !(c[3].equals("true") || c[3].equals("false"))) { throw new IllegalStateException("Invalid province catalog"); }
                result.add(new Province(c[0], c[1], ShippingRegion.valueOf(c[2]), Boolean.parseBoolean(c[3])));
            }
            if (result.size() != 34 || result.stream().map(Province::code).distinct().count() != 34
                    || result.stream().map(p -> normalize(p.name())).distinct().count() != 34) { throw new IllegalStateException("Province catalog must contain 34 unique entries"); }
            return List.copyOf(result);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
