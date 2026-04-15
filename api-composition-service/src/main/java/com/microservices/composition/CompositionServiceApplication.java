package com.microservices.composition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class CompositionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompositionServiceApplication.class, args);
    }
}
