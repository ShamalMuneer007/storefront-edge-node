package com.datamate.storefront.application.port;

import java.time.LocalDate;

public interface FiscalSequence {

    /** Next number for the branch-day, consumed only if the surrounding transaction commits. */
    int next(String branchCode, LocalDate businessDate);
}
