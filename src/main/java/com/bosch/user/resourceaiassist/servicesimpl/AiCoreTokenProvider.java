package com.bosch.user.resourceaiassist.servicesimpl;

import com.bosch.user.resourceaiassist.config.EmbedProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AiCoreTokenProvider {


    private final RestClient rest;

    private final EmbedProps cfg;

    private volatile String token;
    private volatile long   tokenExpEpochMs = 0;

    record TokenResp(String access_token, Long expires_in) {}

   synchronized String getBearer() {
        long skew = 60_000; // 60s safety
        if (token != null && System.currentTimeMillis() < tokenExpEpochMs - skew) return token;

        MultiValueMap<String,String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        TokenResp tr = rest.post()
                .uri(cfg.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(h -> h.setBasicAuth(cfg.getClientId(), cfg.getClientSecret()))
                .body(form)
                .retrieve().body(TokenResp.class);

        token = "Bearer " + tr.access_token();
        long ttlMs = (tr.expires_in() != null ? tr.expires_in() * 1000L : 3600_000);
        tokenExpEpochMs = System.currentTimeMillis() + ttlMs;
        return token;
    }
}


