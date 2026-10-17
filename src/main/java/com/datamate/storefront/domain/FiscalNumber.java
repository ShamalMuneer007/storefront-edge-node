package com.datamate.storefront.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

public record FiscalNumber(String branchCode, LocalDate businessDate, int sequence) {

    private static final Pattern BRANCH_CODE = Pattern.compile("[A-Z0-9]{2,10}");

    public FiscalNumber {
        requireValidBranchCode(branchCode);
        Objects.requireNonNull(businessDate, "businessDate");
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence must start at 1: " + sequence);
        }
    }

    public static void requireValidBranchCode(String branchCode) {
        if (branchCode == null || !BRANCH_CODE.matcher(branchCode).matches()) {
            throw new IllegalArgumentException("Invalid branch code: " + branchCode);
        }
    }

    public String value() {
        return "ORD-%s-%s-%04d".formatted(branchCode, businessDate.format(DateTimeFormatter.BASIC_ISO_DATE), sequence);
    }

    @Override
    public String toString() {
        return value();
    }
}
