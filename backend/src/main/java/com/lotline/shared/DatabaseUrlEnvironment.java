package com.lotline.shared;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.*;

public class DatabaseUrlEnvironment implements EnvironmentPostProcessor {
  public record Connection(String jdbc, String username, String password) {}

  public static Connection parse(String value, String sslmode) {
    try {
      URI uri = URI.create(value);
      if (!Set.of("postgres", "postgresql").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getRawUserInfo() == null
          || uri.getFragment() != null) throw new IllegalArgumentException();
      String[] credentials = uri.getRawUserInfo().split(":", 2);
      if (credentials.length != 2) throw new IllegalArgumentException();
      String database = uri.getPath().substring(1);
      if (!database.matches("[A-Za-z0-9_-]+")
          || !Set.of("disable", "prefer", "require", "verify-ca", "verify-full").contains(sslmode))
        throw new IllegalArgumentException();
      String host = uri.getHost();
      if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
      return new Connection(
          "jdbc:postgresql://"
              + host
              + ":"
              + (uri.getPort() == -1 ? 5432 : uri.getPort())
              + "/"
              + database
              + "?sslmode="
              + sslmode,
          decode(credentials[0]),
          decode(credentials[1]));
    } catch (Exception e) {
      throw new IllegalStateException(
          "DATABASE_URL must be a valid PostgreSQL URI with host, database and percent-encoded"
              + " credentials. Values are not logged.");
    }
  }

  private static String decode(String value) {
    return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
    String uri = env.getProperty("DATABASE_URL");
    if (uri == null || uri.isBlank()) return;
    Connection c = parse(uri, env.getProperty("DB_SSLMODE", "prefer"));
    Map<String, Object> properties = new HashMap<>();
    properties.put("spring.datasource.url", c.jdbc());
    properties.put("spring.datasource.username", c.username());
    properties.put("spring.datasource.password", c.password());
    properties.put("spring.flyway.user", env.getProperty("MIGRATION_DB_USER", c.username()));
    properties.put(
        "spring.flyway.password", env.getProperty("MIGRATION_DB_PASSWORD", c.password()));
    env.getPropertySources()
        .addFirst(new MapPropertySource("lotlineDatabaseConnection", properties));
  }
}
