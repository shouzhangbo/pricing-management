package com.pricing.management.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.pricing.management")
public class PricingManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(PricingManagementApplication.class, args);
    }
}
