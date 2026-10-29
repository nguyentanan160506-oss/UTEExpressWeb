package com.ute.uteexpress.enums;

public enum RateScopeType {
    INTRA_PROVINCE,
    INTER_PROVINCE,
    INTER_REGION,
    SAME_REGION,
    NORTH_CENTRAL,
    CENTRAL_SOUTH,
    NORTH_SOUTH;

    public static java.util.List<RateScopeType> nationwide() {
        return java.util.List.of(INTRA_PROVINCE, SAME_REGION, NORTH_CENTRAL, CENTRAL_SOUTH, NORTH_SOUTH);
    }
    public String getLabel() {
        return switch (this) {
            case INTRA_PROVINCE -> "Nội tỉnh/thành";
            case SAME_REGION -> "Khác tỉnh cùng miền";
            case NORTH_CENTRAL -> "Bắc ↔ Trung";
            case CENTRAL_SOUTH -> "Trung ↔ Nam";
            case NORTH_SOUTH -> "Bắc ↔ Nam";
            case INTER_PROVINCE -> "Liên tỉnh (quy tắc cũ)";
            case INTER_REGION -> "Liên vùng (quy tắc cũ)";
        };
    }
}
