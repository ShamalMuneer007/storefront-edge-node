package com.datamate.storefront.infrastructure.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Currency;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storefront")
public record StorefrontProperties(String branchCode, Currency currency, ZoneId zone, Outbox outbox) {

    public record Outbox(int batchSize, Duration lease) {}
}
