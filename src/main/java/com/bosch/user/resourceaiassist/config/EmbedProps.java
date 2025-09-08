package com.bosch.user.resourceaiassist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "embed")
@Data
public class EmbedProps {
    private String provider;
    private int dim = 3072;
    private int timeoutMs = 10000;
    private String apiBase;
    private String tokenUrl;
    private String clientId;
    private String clientSecret;
    private String deploymentId;
    private String resourceGroup;
    private String embeddingsDeploymentId;
    private String chatDeploymentId;
    private String apiVersion;
}
