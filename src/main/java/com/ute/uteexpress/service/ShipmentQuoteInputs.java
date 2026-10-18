package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.ShipmentCreateForm;
import com.ute.uteexpress.entity.*;

/** Explicit allowlist of request fields; quote totals and references are server-owned. */
final class ShipmentQuoteInputs {
    private ShipmentQuoteInputs() { }
    static void capture(ShipmentCreateForm form, ShipmentQuote quote) {
        quote.setSenderName(form.getSenderName());
        quote.setSenderPhone(form.getSenderPhone());
        quote.setSenderProvince(form.getSenderProvince());
        quote.setSenderDistrict(form.getSenderDistrict());
        quote.setSenderWard(form.getSenderWard());
        quote.setSenderAddress(form.getSenderAddress());
        quote.setReceiverName(form.getReceiverName());
        quote.setReceiverPhone(form.getReceiverPhone());
        quote.setReceiverProvince(form.getReceiverProvince());
        quote.setReceiverDistrict(form.getReceiverDistrict());
        quote.setReceiverWard(form.getReceiverWard());
        quote.setReceiverAddress(form.getReceiverAddress());
        quote.setPackageDescription(form.getPackageDescription());
        quote.setWeightKg(form.getWeightKg());
        quote.setLengthCm(form.getLengthCm());
        quote.setWidthCm(form.getWidthCm());
        quote.setHeightCm(form.getHeightCm());
        quote.setCodAmount(form.getCodAmount());
        quote.setDeclaredValue(form.getDeclaredValue());
        quote.setPaymentPayer(form.getPaymentPayer());
        quote.setNote(form.getNote());
    }
    static ShipmentCreateForm form(ShipmentQuote quote) {
        ShipmentCreateForm form = new ShipmentCreateForm();
        form.setSenderName(quote.getSenderName());
        form.setSenderPhone(quote.getSenderPhone());
        form.setSenderProvince(quote.getSenderProvince());
        form.setSenderDistrict(quote.getSenderDistrict());
        form.setSenderWard(quote.getSenderWard());
        form.setSenderAddress(quote.getSenderAddress());
        form.setReceiverName(quote.getReceiverName());
        form.setReceiverPhone(quote.getReceiverPhone());
        form.setReceiverProvince(quote.getReceiverProvince());
        form.setReceiverDistrict(quote.getReceiverDistrict());
        form.setReceiverWard(quote.getReceiverWard());
        form.setReceiverAddress(quote.getReceiverAddress());
        form.setPackageDescription(quote.getPackageDescription());
        form.setWeightKg(quote.getWeightKg());
        form.setLengthCm(quote.getLengthCm());
        form.setWidthCm(quote.getWidthCm());
        form.setHeightCm(quote.getHeightCm());
        form.setCodAmount(quote.getCodAmount());
        form.setDeclaredValue(quote.getDeclaredValue());
        form.setPaymentPayer(quote.getPaymentPayer());
        form.setNote(quote.getNote());
        form.setShipmentMethod(quote.getShipmentMethod());
        form.setOriginBranchId(quote.getOriginBranch().getId());
        form.setDestinationBranchId(quote.getDestinationBranch().getId());
        form.setServiceId(quote.getService().getId());
        return form;
    }
    static void apply(ShipmentQuote quote, Shipment shipment) {
        shipment.setSenderName(quote.getSenderName());
        shipment.setSenderPhone(quote.getSenderPhone());
        shipment.setSenderProvince(quote.getSenderProvince());
        shipment.setSenderDistrict(quote.getSenderDistrict());
        shipment.setSenderWard(quote.getSenderWard());
        shipment.setSenderAddress(quote.getSenderAddress());
        shipment.setReceiverName(quote.getReceiverName());
        shipment.setReceiverPhone(quote.getReceiverPhone());
        shipment.setReceiverProvince(quote.getReceiverProvince());
        shipment.setReceiverDistrict(quote.getReceiverDistrict());
        shipment.setReceiverWard(quote.getReceiverWard());
        shipment.setReceiverAddress(quote.getReceiverAddress());
        shipment.setPackageDescription(quote.getPackageDescription());
        shipment.setWeightKg(quote.getWeightKg());
        shipment.setLengthCm(quote.getLengthCm());
        shipment.setWidthCm(quote.getWidthCm());
        shipment.setHeightCm(quote.getHeightCm());
        shipment.setCodAmount(quote.getCodAmount());
        shipment.setDeclaredValue(quote.getDeclaredValue());
        shipment.setPaymentPayer(quote.getPaymentPayer());
        shipment.setNote(quote.getNote());
    }
}
