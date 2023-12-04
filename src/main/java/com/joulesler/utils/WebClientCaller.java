package com.joulesler.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WebClientCaller {

    public static WebClient getWebClient(String dnsAddress) {
        WebClient client = WebClient.builder()
                .baseUrl(dnsAddress)
                .build();
        return client;
    }

}