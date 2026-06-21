package com.supplychain.productservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3jConfig {

    private static final Logger log = LoggerFactory.getLogger(Web3jConfig.class);

    @Value("${blockchain.rpc-url:https://rpc-mumbai.maticvigil.com}")
    private String rpcUrl;

    @Value("${blockchain.enabled:true}")
    private boolean blockchainEnabled;

    @Bean
    public Web3j web3j() {
        if (!blockchainEnabled) {
            log.info("Blockchain disabled – creating Web3j with placeholder RPC");
        }
        log.info("Web3j connecting to RPC: {}", rpcUrl);
        return Web3j.build(new HttpService(rpcUrl));
    }
}

