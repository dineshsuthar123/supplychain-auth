package com.supplychain.verificationservice.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Override
    protected String getDatabaseName() {
        return "supplychain";
    }

    @Override
    @Bean
    public MongoClient mongoClient() {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };            // Install the all-trusting trust manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            ConnectionString connectionString = new ConnectionString(mongoUri);
            
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)                    .applyToSocketSettings(builder -> 
                        builder.connectTimeout(20, TimeUnit.SECONDS)
                               .readTimeout(60, TimeUnit.SECONDS))
                    .applyToConnectionPoolSettings(builder -> 
                        builder.maxWaitTime(120, TimeUnit.SECONDS)
                               .maxConnectionIdleTime(0, TimeUnit.SECONDS)
                               .maxConnectionLifeTime(0, TimeUnit.SECONDS)
                               .minSize(0)
                               .maxSize(100))
                    .applyToServerSettings(builder -> 
                        builder.heartbeatFrequency(10, TimeUnit.SECONDS)
                               .minHeartbeatFrequency(500, TimeUnit.MILLISECONDS))
                    .applyToSslSettings(builder -> {
                        builder.enabled(true)
                               .invalidHostNameAllowed(true)
                               .context(sslContext);
                    })
                    .build();

            return MongoClients.create(settings);
        } catch (Exception e) {
            // Fallback to default configuration if SSL setup fails
            return MongoClients.create(mongoUri);
        }
    }
}
