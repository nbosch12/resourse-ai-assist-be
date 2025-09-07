package com.bosch.user.resourceaiassist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(ObjectStorePropsEmbedding.class)
public class AppConfig {
    @Bean @ConfigurationProperties(prefix = "embed")
    public EmbedProps embedProps() { return new EmbedProps(); }

    @Bean("s3Read")
    S3Client s3Read(ObjectStorePropsEmbedding p) {
        return S3Client.builder()
                .endpointOverride(URI.create(p.getEndpoint()))
                .region(Region.of(p.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(p.getReadAccessKey(), p.getReadSecretKey())))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
