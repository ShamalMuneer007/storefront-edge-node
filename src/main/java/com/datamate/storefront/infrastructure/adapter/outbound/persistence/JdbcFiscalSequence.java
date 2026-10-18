package com.datamate.storefront.infrastructure.adapter.outbound.persistence;

import com.datamate.storefront.application.port.outbound.FiscalSequence;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFiscalSequence implements FiscalSequence {

    private final JdbcClient jdbc;

    JdbcFiscalSequence(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int next(String branchCode, LocalDate businessDate) {
        return jdbc.sql("""
                        INSERT INTO fiscal_sequence (branch_code, business_date, last_value)
                        VALUES (:branchCode, :businessDate, 1)
                        ON CONFLICT (branch_code, business_date)
                        DO UPDATE SET last_value = fiscal_sequence.last_value + 1
                        RETURNING last_value
                        """)
                .param("branchCode", branchCode)
                .param("businessDate", businessDate)
                .query(Integer.class)
                .single();
    }
}
