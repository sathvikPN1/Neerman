package com.nirmaan.reimburse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app")
public record AppProperties(
        @DefaultValue("http://localhost:8080") String baseUrl,
        Mail mail,
        Storage storage,
        Bootstrap bootstrap,
        Security security,
        @DefaultValue("false") boolean demoData) {

    public record Mail(@DefaultValue("log") String mode, String from) {
    }

    public record Storage(@DefaultValue("local") String type, @DefaultValue("./data/uploads") String localRoot, S3 s3) {
    }

    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey) {
    }

    public record Bootstrap(String cooEmail, String cooName, String cooPassword) {
    }

    public record Security(@DefaultValue("5") int loginMaxAttempts, @DefaultValue("15") int loginLockMinutes) {
    }
}
