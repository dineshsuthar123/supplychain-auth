package com.lotline;

import static org.assertj.core.api.Assertions.*;

import com.lotline.shared.DatabaseUrlEnvironment;
import org.junit.jupiter.api.Test;

class DatabaseUrlTest {
  @Test
  void encoded_credentials_plus_and_ipv6_produce_jdbc_not_postgres_uri() {
    var c =
        DatabaseUrlEnvironment.parse(
            "postgresql://operator:p%40ss%3Aword+literal@[::1]:5544/lotline_db", "require");
    assertThat(c.jdbc()).isEqualTo("jdbc:postgresql://[::1]:5544/lotline_db?sslmode=require");
    assertThat(c.password()).isEqualTo("p@ss:word+literal");
    assertThat(c.username()).isEqualTo("operator");
  }

  @Test
  void rejects_other_schemes_and_redacts_secrets_in_diagnostics() {
    assertThatThrownBy(
            () -> DatabaseUrlEnvironment.parse("https://user:sensitive@example.com/db", "require"))
        .hasMessageNotContaining("sensitive");
    assertThatThrownBy(
            () ->
                DatabaseUrlEnvironment.parse(
                    "postgres://user:secret@example.com/db", "invented-mode"))
        .hasMessageNotContaining("secret");
  }
}
