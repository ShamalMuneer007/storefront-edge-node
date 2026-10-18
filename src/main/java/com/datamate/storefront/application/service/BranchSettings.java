package com.datamate.storefront.application.service;

import com.datamate.storefront.domain.model.FiscalNumber;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Objects;

public record BranchSettings(String code, Currency currency, ZoneId zone) {

    public BranchSettings {
        FiscalNumber.requireValidBranchCode(code);
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(zone, "zone");
    }
}
