package com.helios.orchestrator.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.helios")
public class HeliosApplication {
    public static void main(String[] args) {
        SpringApplication.run(HeliosApplication.class, args);
    }
}
