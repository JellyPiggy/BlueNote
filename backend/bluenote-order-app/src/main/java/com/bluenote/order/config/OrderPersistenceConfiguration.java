package com.bluenote.order.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.bluenote.order.**.infrastructure.mapper")
public class OrderPersistenceConfiguration {
}
