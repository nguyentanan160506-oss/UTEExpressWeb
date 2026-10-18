package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shipment_quotes",
    schema = "dbo",
    uniqueConstraints = {@UniqueConstraint(name = "UQ_shipment_quotes_token", columnNames = {"token"})},
    indexes = {@Index(name = "IX_shipment_quotes_owner", columnList = "requester_id, created_at DESC"), @Index(name = "IX_shipment_quotes_expiry", columnList = "expires_at, consumed_at")},
    check = {@CheckConstraint(name = "CK_quotes_method", constraint = "shipment_method IN ('HOME_PICKUP', 'BRANCH_DROPOFF')"),
        @CheckConstraint(name = "CK_quotes_payer", constraint = "payment_payer IN ('SENDER', 'RECEIVER')"),
        @CheckConstraint(name = "CK_quotes_weight", constraint = "weight_kg > 0"),
        @CheckConstraint(name = "CK_quotes_length", constraint = "length_cm IS NULL OR length_cm > 0"),
        @CheckConstraint(name = "CK_quotes_width", constraint = "width_cm IS NULL OR width_cm > 0"),
        @CheckConstraint(name = "CK_quotes_height", constraint = "height_cm IS NULL OR height_cm > 0"),
        @CheckConstraint(name = "CK_quotes_amounts", constraint = "cod_amount >= 0 AND declared_value >= 0 AND shipping_fee >= 0 AND pickup_fee >= 0 AND insurance_fee >= 0 AND total_fee = shipping_fee + pickup_fee + insurance_fee"),
        @CheckConstraint(name = "CK_quotes_pickup", constraint = "(shipment_method = 'HOME_PICKUP' AND pickup_fee_rule_id IS NOT NULL AND pickup_fee > 0) OR (shipment_method = 'BRANCH_DROPOFF' AND pickup_fee_rule_id IS NULL AND pickup_fee = 0)"),
        @CheckConstraint(name = "CK_quotes_expiry", constraint = "expires_at > created_at"),
        @CheckConstraint(name = "CK_quotes_consumed", constraint = "consumed_at IS NULL OR (consumed_at >= created_at AND consumed_at < expires_at)")})
public class ShipmentQuote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_quotes_requester"))
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_branch_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_quotes_origin_branch"))
    private Branch originBranch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_branch_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_quotes_destination_branch"))
    private Branch destinationBranch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_hub_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_quotes_origin_hub"))
    private Branch originHub;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_hub_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_quotes_destination_hub"))
    private Branch destinationHub;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_quotes_service"))
    private ShippingService service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipping_rate_rule_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_quotes_shipping_rate"))
    private ShippingRateRule shippingRateRule;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "pickup_fee_rule_id", nullable = true, updatable = false, foreignKey = @ForeignKey(name = "FK_quotes_pickup_rule"))
    private PickupFeeRule pickupFeeRule;

    @Column(name = "token", nullable = false, updatable = false, length = 32)
    private String token;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "shipment_method", nullable = false, updatable = false, length = 30)
    private ShipmentMethod shipmentMethod;

    @Nationalized @Column(name = "sender_name", nullable = false, updatable = false, length = 100)
    private String senderName;

    @Column(name = "sender_phone", nullable = false, updatable = false, length = 20)
    private String senderPhone;

    @Nationalized @Column(name = "sender_province", nullable = false, updatable = false, length = 100)
    private String senderProvince;

    @Nationalized @Column(name = "sender_district", nullable = false, updatable = false, length = 100)
    private String senderDistrict;

    @Nationalized @Column(name = "sender_ward", nullable = false, updatable = false, length = 100)
    private String senderWard;

    @Nationalized @Column(name = "sender_address", nullable = false, updatable = false, length = 255)
    private String senderAddress;

    @Nationalized @Column(name = "receiver_name", nullable = false, updatable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, updatable = false, length = 20)
    private String receiverPhone;

    @Nationalized @Column(name = "receiver_province", nullable = false, updatable = false, length = 100)
    private String receiverProvince;

    @Nationalized @Column(name = "receiver_district", nullable = false, updatable = false, length = 100)
    private String receiverDistrict;

    @Nationalized @Column(name = "receiver_ward", nullable = false, updatable = false, length = 100)
    private String receiverWard;

    @Nationalized @Column(name = "receiver_address", nullable = false, updatable = false, length = 255)
    private String receiverAddress;

    @Nationalized @Column(name = "package_description", nullable = false, updatable = false, length = 255)
    private String packageDescription;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "weight_kg", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal weightKg;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "length_cm", nullable = true, updatable = false, precision = 10, scale = 2)
    private BigDecimal lengthCm;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "width_cm", nullable = true, updatable = false, precision = 10, scale = 2)
    private BigDecimal widthCm;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "height_cm", nullable = true, updatable = false, precision = 10, scale = 2)
    private BigDecimal heightCm;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "cod_amount", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal codAmount;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "declared_value", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal declaredValue;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "payment_payer", nullable = false, updatable = false, length = 30)
    private PaymentPayer paymentPayer;

    @Nationalized @Column(name = "note", nullable = true, updatable = false, length = 500)
    private String note;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "shipping_fee", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal shippingFee;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "pickup_fee", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal pickupFee;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "insurance_fee", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal insuranceFee;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "total_fee", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal totalFee;

    @JdbcTypeCode(SqlTypes.TIMESTAMP) @ColumnDefault("SYSUTCDATETIME()") @Column(name = "created_at", nullable = false, updatable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "expires_at", nullable = false, updatable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime expiresAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "consumed_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime consumedAt;

    @PrePersist void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
        if (createdAt == null) { createdAt = now; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getRequester() { return requester; }
    public void setRequester(User value) { this.requester = value; }
    public Branch getOriginBranch() { return originBranch; }
    public void setOriginBranch(Branch value) { this.originBranch = value; }
    public Branch getDestinationBranch() { return destinationBranch; }
    public void setDestinationBranch(Branch value) { this.destinationBranch = value; }
    public Branch getOriginHub() { return originHub; }
    public void setOriginHub(Branch value) { this.originHub = value; }
    public Branch getDestinationHub() { return destinationHub; }
    public void setDestinationHub(Branch value) { this.destinationHub = value; }
    public ShippingService getService() { return service; }
    public void setService(ShippingService value) { this.service = value; }
    public ShippingRateRule getShippingRateRule() { return shippingRateRule; }
    public void setShippingRateRule(ShippingRateRule value) { this.shippingRateRule = value; }
    public PickupFeeRule getPickupFeeRule() { return pickupFeeRule; }
    public void setPickupFeeRule(PickupFeeRule value) { this.pickupFeeRule = value; }
    public String getToken() { return token; }
    public void setToken(String value) { this.token = value; }
    public ShipmentMethod getShipmentMethod() { return shipmentMethod; }
    public void setShipmentMethod(ShipmentMethod value) { this.shipmentMethod = value; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String value) { this.senderName = value; }
    public String getSenderPhone() { return senderPhone; }
    public void setSenderPhone(String value) { this.senderPhone = value; }
    public String getSenderProvince() { return senderProvince; }
    public void setSenderProvince(String value) { this.senderProvince = value; }
    public String getSenderDistrict() { return senderDistrict; }
    public void setSenderDistrict(String value) { this.senderDistrict = value; }
    public String getSenderWard() { return senderWard; }
    public void setSenderWard(String value) { this.senderWard = value; }
    public String getSenderAddress() { return senderAddress; }
    public void setSenderAddress(String value) { this.senderAddress = value; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String value) { this.receiverName = value; }
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String value) { this.receiverPhone = value; }
    public String getReceiverProvince() { return receiverProvince; }
    public void setReceiverProvince(String value) { this.receiverProvince = value; }
    public String getReceiverDistrict() { return receiverDistrict; }
    public void setReceiverDistrict(String value) { this.receiverDistrict = value; }
    public String getReceiverWard() { return receiverWard; }
    public void setReceiverWard(String value) { this.receiverWard = value; }
    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String value) { this.receiverAddress = value; }
    public String getPackageDescription() { return packageDescription; }
    public void setPackageDescription(String value) { this.packageDescription = value; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal value) { this.weightKg = value; }
    public BigDecimal getLengthCm() { return lengthCm; }
    public void setLengthCm(BigDecimal value) { this.lengthCm = value; }
    public BigDecimal getWidthCm() { return widthCm; }
    public void setWidthCm(BigDecimal value) { this.widthCm = value; }
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal value) { this.heightCm = value; }
    public BigDecimal getCodAmount() { return codAmount; }
    public void setCodAmount(BigDecimal value) { this.codAmount = value; }
    public BigDecimal getDeclaredValue() { return declaredValue; }
    public void setDeclaredValue(BigDecimal value) { this.declaredValue = value; }
    public PaymentPayer getPaymentPayer() { return paymentPayer; }
    public void setPaymentPayer(PaymentPayer value) { this.paymentPayer = value; }
    public String getNote() { return note; }
    public void setNote(String value) { this.note = value; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal value) { this.shippingFee = value; }
    public BigDecimal getPickupFee() { return pickupFee; }
    public void setPickupFee(BigDecimal value) { this.pickupFee = value; }
    public BigDecimal getInsuranceFee() { return insuranceFee; }
    public void setInsuranceFee(BigDecimal value) { this.insuranceFee = value; }
    public BigDecimal getTotalFee() { return totalFee; }
    public void setTotalFee(BigDecimal value) { this.totalFee = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { this.expiresAt = value; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(LocalDateTime value) { this.consumedAt = value; }
}
