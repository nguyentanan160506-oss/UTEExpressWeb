package com.ute.uteexpress.dto.shipment;

import jakarta.validation.constraints.*;

public class QuoteConfirmationForm {
    @NotNull @Pattern(regexp = "[a-f0-9]{32}", message = "Mã báo giá không hợp lệ.")
    private String quoteToken;
    public String getQuoteToken() { return quoteToken; }
    public void setQuoteToken(String value) { quoteToken = value; }
}
