package com.bosch.user.resourceaiassist.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "object-store")
public class ObjectStorePropsEmbedding {
    String bucket, endpoint, region, readAccessKey, readSecretKey;
}
