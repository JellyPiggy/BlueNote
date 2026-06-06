package com.bluenote.content.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.bluenote.content.**.infrastructure.mapper")
public class ContentPersistenceConfiguration {
}

