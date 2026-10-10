package com.pricing.management.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** On a separate class so @WebMvcTest slices (which load only the bootstrap class) do not register mapper beans without a SqlSessionFactory. */
@Configuration
@MapperScan("com.pricing.management.infrastructure.mapper")
public class MyBatisConfig {
}
