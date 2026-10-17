package com.datamate.storefront.infrastructure.config;

import com.datamate.storefront.application.BranchSettings;
import com.datamate.storefront.application.PlaceOrderService;
import com.datamate.storefront.application.port.FiscalSequence;
import com.datamate.storefront.application.port.IdGenerator;
import com.datamate.storefront.application.port.IdempotencyKeys;
import com.datamate.storefront.application.port.Inventory;
import com.datamate.storefront.application.port.KitchenOutbox;
import com.datamate.storefront.application.port.Orders;
import com.datamate.storefront.application.port.Transactions;
import com.datamate.storefront.infrastructure.UuidV7Generator;
import com.datamate.storefront.infrastructure.kitchen.KitchenDisplay;
import com.datamate.storefront.infrastructure.kitchen.OutboxRelay;
import com.datamate.storefront.infrastructure.persistence.SpringTransactions;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(StorefrontProperties.class)
class StorefrontConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IdGenerator idGenerator(Clock clock) {
        return new UuidV7Generator(clock);
    }

    @Bean
    Transactions transactions(PlatformTransactionManager transactionManager) {
        return new SpringTransactions(transactionManager);
    }

    @Bean
    BranchSettings branchSettings(StorefrontProperties properties) {
        return new BranchSettings(properties.branchCode(), properties.currency(), properties.zone());
    }

    @Bean
    PlaceOrderService placeOrderService(
            Transactions transactions,
            IdempotencyKeys idempotencyKeys,
            Inventory inventory,
            FiscalSequence fiscalSequence,
            Orders orders,
            KitchenOutbox kitchenOutbox,
            IdGenerator ids,
            BranchSettings branch,
            Clock clock) {
        return new PlaceOrderService(
                transactions, idempotencyKeys, inventory, fiscalSequence, orders, kitchenOutbox, ids, branch, clock);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcClient jdbc, KitchenDisplay kitchen, StorefrontProperties properties) {
        return new OutboxRelay(jdbc, kitchen, properties.outbox().batchSize(), properties.outbox().lease());
    }
}
