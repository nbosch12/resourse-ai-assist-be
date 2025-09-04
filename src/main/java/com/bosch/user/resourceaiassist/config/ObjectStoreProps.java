package com.bosch.user.resourceaiassist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "objectstore")
public class ObjectStoreProps {
    String bucket, endpoint, region, writeAccessKey, writeSecretKey;
}
