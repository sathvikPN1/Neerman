package com.nirmaan.reimburse.config;

import com.nirmaan.reimburse.common.storage.LocalDiskStorageService;
import com.nirmaan.reimburse.common.storage.S3StorageService;
import com.nirmaan.reimburse.common.storage.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.nio.file.Path;

@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    StorageService localStorageService(AppProperties props) {
        return new LocalDiskStorageService(Path.of(props.storage().localRoot()));
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    StorageService s3StorageService(AppProperties props) {
        AppProperties.S3 s3 = props.storage().s3();
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(s3.endpoint()))
                .region(Region.of(s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        return new S3StorageService(client, s3.bucket());
    }
}
