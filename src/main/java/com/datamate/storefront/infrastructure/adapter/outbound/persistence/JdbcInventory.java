package com.datamate.storefront.infrastructure.adapter.outbound.persistence;

import com.datamate.storefront.application.port.outbound.Inventory;
import com.datamate.storefront.domain.model.Money;
import com.datamate.storefront.domain.exception.OutOfStockException;
import com.datamate.storefront.domain.model.Product;
import com.datamate.storefront.domain.model.TaxRate;
import com.datamate.storefront.domain.exception.UnknownProductException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInventory implements Inventory {

    private final JdbcClient jdbc;

    JdbcInventory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Product reserve(String sku, int quantity) {
        return jdbc.sql("""
                        UPDATE product
                        SET stock = stock - :quantity, updated_at = now()
                        WHERE sku = :sku AND stock >= :quantity
                        RETURNING sku, name, unit_price, currency, tax_rate, requires_preparation
                        """)
                .param("sku", sku)
                .param("quantity", quantity)
                .query(JdbcInventory::toProduct)
                .optional()
                .orElseThrow(() -> exists(sku) ? new OutOfStockException(sku) : new UnknownProductException(sku));
    }

    private boolean exists(String sku) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM product WHERE sku = :sku)")
                .param("sku", sku)
                .query(Boolean.class)
                .single();
    }

    private static Product toProduct(ResultSet rs, int row) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return new Product(
                rs.getString("sku"),
                rs.getString("name"),
                new Money(rs.getBigDecimal("unit_price"), currency),
                new TaxRate(rs.getBigDecimal("tax_rate")),
                rs.getBoolean("requires_preparation"));
    }
}
