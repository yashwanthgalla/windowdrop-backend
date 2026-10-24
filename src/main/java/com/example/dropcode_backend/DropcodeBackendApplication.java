package com.example.dropcode_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DropcodeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DropcodeBackendApplication.class, args);
    }
}
