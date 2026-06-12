package com.bluenote.social;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.bluenote")
public class SocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialApplication.class, args);
    }
}
