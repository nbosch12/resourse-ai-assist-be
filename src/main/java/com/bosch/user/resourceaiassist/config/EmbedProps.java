package com.bosch.user.resourceaiassist.config;

import lombok.Data;

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
}
