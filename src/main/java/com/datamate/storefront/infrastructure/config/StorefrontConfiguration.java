package com.datamate.storefront.infrastructure.config;

import com.datamate.storefront.application.port.inbound.FindOrderUseCase;
import com.datamate.storefront.application.port.inbound.PlaceOrderUseCase;
import com.datamate.storefront.application.port.outbound.FiscalSequence;
import com.datamate.storefront.application.port.outbound.IdGenerator;
import com.datamate.storefront.application.port.outbound.IdempotencyKeys;
import com.datamate.storefront.application.port.outbound.Inventory;
import com.datamate.storefront.application.port.outbound.KitchenOutbox;
import com.datamate.storefront.application.port.outbound.Orders;
import com.datamate.storefront.application.port.outbound.Transactions;
import com.datamate.storefront.application.service.BranchSettings;
import com.datamate.storefront.application.service.FindOrderService;
import com.datamate.storefront.application.service.PlaceOrderService;
import com.datamate.storefront.infrastructure.adapter.outbound.id.UuidV7Generator;
import com.datamate.storefront.infrastructure.adapter.outbound.kitchen.KitchenDisplay;
import com.datamate.storefront.infrastructure.adapter.outbound.kitchen.OutboxRelay;
import com.datamate.storefront.infrastructure.adapter.outbound.persistence.JdbcOutboxStore;
import com.datamate.storefront.infrastructure.adapter.outbound.persistence.SpringTransactions;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    PlaceOrderUseCase placeOrderUseCase(
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
    FindOrderUseCase findOrderUseCase(Orders orders) {
        return new FindOrderService(orders);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcOutboxStore outbox, KitchenDisplay kitchen, StorefrontProperties properties) {
        return new OutboxRelay(outbox, kitchen, properties.outbox().batchSize(), properties.outbox().lease());
    }
}
