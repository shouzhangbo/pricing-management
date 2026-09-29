package com.pricing.management.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.pricing.management")
@MapperScan("com.pricing.management.infrastructure.mapper")
public class PricingManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(PricingManagementApplication.class, args);
    }
}
