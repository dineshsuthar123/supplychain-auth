package com.supplychain.productservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.crypto.Credentials;

@Configuration
public class Web3jConfig {

    @Value("${web3j.client-address:}")
    private String clientAddress;

    @Value("${web3j.private-key:0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}")
    private String privateKey;

    @Bean
    @ConditionalOnProperty(name = "web3j.client-address", matchIfMissing = false)
    public Web3j web3j() {
        if (clientAddress == null || clientAddress.isBlank()) {
            return null;
        }
        return Web3j.build(new HttpService(clientAddress));
    }

    @Bean
    public Credentials credentials() {
        return Credentials.create(privateKey);
    }
}
