package com.bosch.user.resourceaiassist.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(EmbedProps.class)
class HttpConfig {
    @Bean
    RestClient restClient(RestClient.Builder b) { return b.build(); }
}
