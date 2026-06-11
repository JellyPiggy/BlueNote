package com.bluenote.social.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.bluenote.social.**.infrastructure.mapper")
public class SocialPersistenceConfiguration {
}
