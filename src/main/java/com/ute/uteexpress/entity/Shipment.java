package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.PaymentPayer;
import com.ute.uteexpress.enums.ShipmentPaymentStatus;
import com.ute.uteexpress.enums.ShipmentStatus;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "shipments",
    schema = "dbo",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_shipments_tracking", columnNames = {"tracking_number"})
    },
    indexes = {
        @Index(name = "IX_shipments_sender", columnList = "sender_id, created_at DESC"),
        @Index(name = "IX_shipments_status", columnList = "status"),
        @Index(name = "IX_shipments_branches", columnList = "origin_branch_id, destination_branch_id, current_branch_id")
    },
    check = {
        @CheckConstraint(name = "CK_shipments_weight", constraint = "weight_kg > 0"),
        @CheckConstraint(name = "CK_shipments_length", constraint = "length_cm IS NULL OR length_cm > 0"),
        @CheckConstraint(name = "CK_shipments_width", constraint = "width_cm IS NULL OR width_cm > 0"),
        @CheckConstraint(name = "CK_shipments_height", constraint = "height_cm IS NULL OR height_cm > 0"),
        @CheckConstraint(name = "CK_shipments_cod", constraint = "cod_amount >= 0"),
        @CheckConstraint(name = "CK_shipments_declared", constraint = "declared_value >= 0"),
        @CheckConstraint(name = "CK_shipments_shipping_fee", constraint = "shipping_fee >= 0"),
        @CheckConstraint(name = "CK_shipments_insurance_fee", constraint = "insurance_fee >= 0"),
        @CheckConstraint(name = "CK_shipments_total_fee", constraint = "total_fee >= 0"),
        @CheckConstraint(name = "CK_shipments_status", constraint = "status IN ( 'CREATED', 'CONFIRMED', 'PICKUP_ASSIGNED', 'PICKED_UP', 'AT_ORIGIN_HUB', 'IN_TRANSIT', 'AT_DESTINATION_HUB', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'RETURNING', 'RETURNED', 'CANCELLED' )"),
        @CheckConstraint(name = "CK_shipments_attempts", constraint = "delivery_attempt_count >= 0"),
        @CheckConstraint(name = "CK_shipments_max_attempts", constraint = "max_delivery_attempts >= 1"),
        @CheckConstraint(name = "CK_shipments_payment_status", constraint = "payment_status IN ('UNPAID', 'PAID', 'REFUNDED')"),
        @CheckConstraint(name = "CK_shipments_payer", constraint = "payment_payer IN ('SENDER', 'RECEIVER')")
    }
)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tracking_number", nullable = false, length = 50)
    private String trackingNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, foreignKey = @ForeignKey(name = "FK_shipments_sender"))
    private User sender;

    @Nationalized
    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Column(name = "sender_phone", nullable = false, length = 20)
    private String senderPhone;

    @Nationalized
    @Column(name = "sender_province", nullable = false, length = 100)
    private String senderProvince;

    @Nationalized
    @Column(name = "sender_district", nullable = false, length = 100)
    private String senderDistrict;

    @Nationalized
    @Column(name = "sender_ward", nullable = false, length = 100)
    private String senderWard;

    @Nationalized
    @Column(name = "sender_address", nullable = false, length = 255)
    private String senderAddress;

    @Nationalized
    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Nationalized
    @Column(name = "receiver_province", nullable = false, length = 100)
    private String receiverProvince;

    @Nationalized
    @Column(name = "receiver_district", nullable = false, length = 100)
    private String receiverDistrict;

    @Nationalized
    @Column(name = "receiver_ward", nullable = false, length = 100)
    private String receiverWard;

    @Nationalized
    @Column(name = "receiver_address", nullable = false, length = 255)
    private String receiverAddress;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_shipments_origin_branch"))
    private Branch originBranch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_shipments_dest_branch"))
    private Branch destinationBranch;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "current_branch_id", nullable = true, foreignKey = @ForeignKey(name = "FK_shipments_current_branch"))
    private Branch currentBranch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "FK_shipments_service"))
    private ShippingService service;

    @Nationalized
    @Column(name = "package_description", nullable = false, length = 255)
    private String packageDescription;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "weight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal weightKg;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "length_cm", nullable = true, precision = 10, scale = 2)
    private BigDecimal lengthCm;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "width_cm", nullable = true, precision = 10, scale = 2)
    private BigDecimal widthCm;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "height_cm", nullable = true, precision = 10, scale = 2)
    private BigDecimal heightCm;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "cod_amount", nullable = false, precision = 18, scale = 2)
    @ColumnDefault("0.00")
    private BigDecimal codAmount = new BigDecimal("0.00");

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "declared_value", nullable = false, precision = 18, scale = 2)
    @ColumnDefault("0.00")
    private BigDecimal declaredValue = new BigDecimal("0.00");

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "shipping_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal shippingFee;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "insurance_fee", nullable = false, precision = 18, scale = 2)
    @ColumnDefault("0.00")
    private BigDecimal insuranceFee = new BigDecimal("0.00");

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "total_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalFee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 50)
    @ColumnDefault("'CREATED'")
    private ShipmentStatus status = ShipmentStatus.CREATED;

    @Column(name = "delivery_attempt_count", nullable = false)
    @ColumnDefault("0")
    private Integer deliveryAttemptCount = 0;

    @Column(name = "max_delivery_attempts", nullable = false)
    @ColumnDefault("3")
    private Integer maxDeliveryAttempts = 3;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_status", nullable = false, length = 30)
    @ColumnDefault("'UNPAID'")
    private ShipmentPaymentStatus paymentStatus = ShipmentPaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_payer", nullable = false, length = 30)
    @ColumnDefault("'SENDER'")
    private PaymentPayer paymentPayer = PaymentPayer.SENDER;

    @Nationalized
    @Column(name = "note", nullable = true, length = 500)
    private String note;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime updatedAt;

    public Shipment() {
    }

    @PrePersist
    void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
        if (codAmount == null) {
            codAmount = new BigDecimal("0.00");
        }
        if (declaredValue == null) {
            declaredValue = new BigDecimal("0.00");
        }
        if (insuranceFee == null) {
            insuranceFee = new BigDecimal("0.00");
        }
        if (status == null) {
            status = ShipmentStatus.CREATED;
        }
        if (deliveryAttemptCount == null) {
            deliveryAttemptCount = 0;
        }
        if (maxDeliveryAttempts == null) {
            maxDeliveryAttempts = 3;
        }
        if (paymentStatus == null) {
            paymentStatus = ShipmentPaymentStatus.UNPAID;
        }
        if (paymentPayer == null) {
            paymentPayer = PaymentPayer.SENDER;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = UtcTimestamps.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderPhone() {
        return senderPhone;
    }

    public void setSenderPhone(String senderPhone) {
        this.senderPhone = senderPhone;
    }

    public String getSenderProvince() {
        return senderProvince;
    }

    public void setSenderProvince(String senderProvince) {
        this.senderProvince = senderProvince;
    }

    public String getSenderDistrict() {
        return senderDistrict;
    }

    public void setSenderDistrict(String senderDistrict) {
        this.senderDistrict = senderDistrict;
    }

    public String getSenderWard() {
        return senderWard;
    }

    public void setSenderWard(String senderWard) {
        this.senderWard = senderWard;
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getReceiverProvince() {
        return receiverProvince;
    }

    public void setReceiverProvince(String receiverProvince) {
        this.receiverProvince = receiverProvince;
    }

    public String getReceiverDistrict() {
        return receiverDistrict;
    }

    public void setReceiverDistrict(String receiverDistrict) {
        this.receiverDistrict = receiverDistrict;
    }

    public String getReceiverWard() {
        return receiverWard;
    }

    public void setReceiverWard(String receiverWard) {
        this.receiverWard = receiverWard;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public Branch getOriginBranch() {
        return originBranch;
    }

    public void setOriginBranch(Branch originBranch) {
        this.originBranch = originBranch;
    }

    public Branch getDestinationBranch() {
        return destinationBranch;
    }

    public void setDestinationBranch(Branch destinationBranch) {
        this.destinationBranch = destinationBranch;
    }

    public Branch getCurrentBranch() {
        return currentBranch;
    }

    public void setCurrentBranch(Branch currentBranch) {
        this.currentBranch = currentBranch;
    }

    public ShippingService getService() {
        return service;
    }

    public void setService(ShippingService service) {
        this.service = service;
    }

    public String getPackageDescription() {
        return packageDescription;
    }

    public void setPackageDescription(String packageDescription) {
        this.packageDescription = packageDescription;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public void setLengthCm(BigDecimal lengthCm) {
        this.lengthCm = lengthCm;
    }

    public BigDecimal getWidthCm() {
        return widthCm;
    }

    public void setWidthCm(BigDecimal widthCm) {
        this.widthCm = widthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getCodAmount() {
        return codAmount;
    }

    public void setCodAmount(BigDecimal codAmount) {
        this.codAmount = codAmount;
    }

    public BigDecimal getDeclaredValue() {
        return declaredValue;
    }

    public void setDeclaredValue(BigDecimal declaredValue) {
        this.declaredValue = declaredValue;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
    }

    public BigDecimal getInsuranceFee() {
        return insuranceFee;
    }

    public void setInsuranceFee(BigDecimal insuranceFee) {
        this.insuranceFee = insuranceFee;
    }

    public BigDecimal getTotalFee() {
        return totalFee;
    }

    public void setTotalFee(BigDecimal totalFee) {
        this.totalFee = totalFee;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public Integer getDeliveryAttemptCount() {
        return deliveryAttemptCount;
    }

    public void setDeliveryAttemptCount(Integer deliveryAttemptCount) {
        this.deliveryAttemptCount = deliveryAttemptCount;
    }

    public Integer getMaxDeliveryAttempts() {
        return maxDeliveryAttempts;
    }

    public void setMaxDeliveryAttempts(Integer maxDeliveryAttempts) {
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    public ShipmentPaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(ShipmentPaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public PaymentPayer getPaymentPayer() {
        return paymentPayer;
    }

    public void setPaymentPayer(PaymentPayer paymentPayer) {
        this.paymentPayer = paymentPayer;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
