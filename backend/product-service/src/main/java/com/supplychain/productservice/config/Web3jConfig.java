package com.supplychain.productservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.crypto.Credentials;

@Configuration
public class Web3jConfig {
    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService("${web3j.client-address}"));
    }

    @Bean
    public Credentials credentials() {
        // For demo: use a dummy private key (replace in production)
        return Credentials.create("0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }
}
