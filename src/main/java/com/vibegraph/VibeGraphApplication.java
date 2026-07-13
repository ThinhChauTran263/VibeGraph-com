package com.vibegraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VibeGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(VibeGraphApplication.class, args);
    }

}
