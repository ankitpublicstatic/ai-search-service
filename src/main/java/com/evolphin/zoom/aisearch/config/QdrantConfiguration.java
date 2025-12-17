package com.evolphin.zoom.aisearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

@Configuration
public class QdrantConfiguration {

  @Value("${qdrant.host:localhost}")
  private String qdrantHost;

  @Value("${qdrant.port:6334}") // gRPC port is usually HTTP port + 1
  private int qdrantPort;

  @Bean
  public QdrantClient qdrantClient() {
    // In production, you would also configure API keys / TLS here
    return new QdrantClient(QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort).build());
  }
}
