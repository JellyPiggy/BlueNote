package com.bluenote.member.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.bluenote.member.**.infrastructure.mapper")
public class MemberPersistenceConfiguration {
}
