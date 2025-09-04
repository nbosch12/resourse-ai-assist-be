package com.bosch.user.resourceaiassist.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
@Configuration
@EnableConfigurationProperties(ObjectStoreProps.class)
class S3Config {
    @Bean @Qualifier("s3Write")
    S3Client s3Write(ObjectStoreProps p) {
        return S3Client.builder()
                .endpointOverride(URI.create(p.getEndpoint()))
                .region(Region.of(p.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(p.getWriteAccessKey(), p.getWriteSecretKey())))
                .httpClientBuilder(UrlConnectionHttpClient.builder()) // no proxy here
                .build();
    }
}

