package com.nirmaan.reimburse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NirmaanReimburseApplication {

    public static void main(String[] args) {
        SpringApplication.run(NirmaanReimburseApplication.class, args);
    }
}
