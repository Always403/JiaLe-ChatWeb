package com.example.jialechatweb.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.example.jialechatweb")
public class MyBatisConfig {
}
