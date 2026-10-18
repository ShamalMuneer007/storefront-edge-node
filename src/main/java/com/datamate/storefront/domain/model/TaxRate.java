package com.datamate.storefront.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record TaxRate(BigDecimal percent) {

    private static final BigDecimal MAX = BigDecimal.valueOf(100);

    public TaxRate {
        Objects.requireNonNull(percent, "percent");
        percent = percent.setScale(2, RoundingMode.UNNECESSARY);
        if (percent.signum() < 0 || percent.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 100: " + percent);
        }
    }

    public static TaxRate of(String percent) {
        return new TaxRate(new BigDecimal(percent));
    }
}
