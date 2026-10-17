package com.datamate.storefront;

import org.springframework.boot.SpringApplication;

public class TestStorefrontNodeApplication {

    public static void main(String[] args) {
        SpringApplication.from(StorefrontNodeApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("demo")
                .run(args);
    }
}
