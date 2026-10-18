package com.datamate.storefront.infrastructure.adapter.outbound.persistence;

import com.datamate.storefront.application.port.outbound.Orders;
import com.datamate.storefront.domain.model.FiscalNumber;
import com.datamate.storefront.domain.model.Money;
import com.datamate.storefront.domain.model.Order;
import com.datamate.storefront.domain.model.OrderLine;
import com.datamate.storefront.domain.model.Product;
import com.datamate.storefront.domain.model.TaxRate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrders implements Orders {

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;

    JdbcOrders(JdbcClient jdbc, JdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    @Override
    public void save(Order order) {
        FiscalNumber fiscal = order.fiscalNumber();
        jdbc.sql("""
                        INSERT INTO customer_order (id, branch_code, terminal_id, business_date, fiscal_sequence,
                                                    fiscal_number, currency, subtotal, tax, total, placed_at)
                        VALUES (:id, :branchCode, :terminalId, :businessDate, :sequence,
                                :fiscalNumber, :currency, :subtotal, :tax, :total, :placedAt)
                        """)
                .param("id", order.id())
                .param("branchCode", fiscal.branchCode())
                .param("terminalId", order.terminalId())
                .param("businessDate", fiscal.businessDate())
                .param("sequence", fiscal.sequence())
                .param("fiscalNumber", fiscal.value())
                .param("currency", order.currency().getCurrencyCode())
                .param("subtotal", order.subtotal().amount())
                .param("tax", order.tax().amount())
                .param("total", order.total().amount())
                .param("placedAt", OffsetDateTime.ofInstant(order.placedAt(), ZoneOffset.UTC))
                .update();

        List<Object[]> lines = new ArrayList<>();
        for (int i = 0; i < order.lines().size(); i++) {
            OrderLine line = order.lines().get(i);
            Product product = line.product();
            lines.add(new Object[] {
                order.id(), i + 1, product.sku(), product.name(), line.quantity(),
                product.unitPrice().amount(), product.taxRate().percent(), product.requiresPreparation()
            });
        }
        batch.batchUpdate("""
                INSERT INTO order_line (order_id, line_no, sku, name, quantity, unit_price, tax_rate, requires_preparation)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, lines);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jdbc.sql("""
                        SELECT id, branch_code, terminal_id, business_date, fiscal_sequence, currency, placed_at
                        FROM customer_order WHERE id = :id
                        """)
                .param("id", id)
                .query(JdbcOrders::toHeader)
                .optional()
                .map(header -> new Order(
                        header.id(), header.terminalId(), header.fiscalNumber(), header.currency(),
                        findLines(header.id(), header.currency()), header.placedAt()));
    }

    private static OrderHeader toHeader(ResultSet rs, int row) throws SQLException {
        return new OrderHeader(
                rs.getObject("id", UUID.class),
                rs.getString("terminal_id"),
                new FiscalNumber(
                        rs.getString("branch_code"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getInt("fiscal_sequence")),
                Currency.getInstance(rs.getString("currency")),
                rs.getObject("placed_at", OffsetDateTime.class).toInstant());
    }

    private List<OrderLine> findLines(UUID orderId, Currency currency) {
        return jdbc.sql("""
                        SELECT sku, name, quantity, unit_price, tax_rate, requires_preparation
                        FROM order_line WHERE order_id = :orderId ORDER BY line_no
                        """)
                .param("orderId", orderId)
                .query((rs, row) -> new OrderLine(
                        new Product(
                                rs.getString("sku"),
                                rs.getString("name"),
                                new Money(rs.getBigDecimal("unit_price"), currency),
                                new TaxRate(rs.getBigDecimal("tax_rate")),
                                rs.getBoolean("requires_preparation")),
                        rs.getInt("quantity")))
                .list();
    }

    private record OrderHeader(
            UUID id, String terminalId, FiscalNumber fiscalNumber, Currency currency, Instant placedAt) {}
}
