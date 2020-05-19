package com.amazon.pay.sample.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("classpath:merchant.properties")
public class AmazonPaySampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(AmazonPaySampleApplication.class, args);
    }
}
