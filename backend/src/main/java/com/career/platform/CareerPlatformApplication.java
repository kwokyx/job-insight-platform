package com.career.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CareerPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(CareerPlatformApplication.class, args);
    }
}
