package com.lotline.shared;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Db {
  public final JdbcTemplate jdbc;

  public Db(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.query(
        sql,
        (rs, index) -> {
          Map<String, Object> row = new LinkedHashMap<>();
          var meta = rs.getMetaData();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            Object v = rs.getObject(i);
            if (v instanceof BigDecimal n) v = Quantities.text(n);
            else if (v instanceof Timestamp t) v = t.toInstant().toString();
            else if (v instanceof UUID || v instanceof java.sql.Date) v = v.toString();
            else if (v instanceof java.sql.Array a)
              v = Arrays.stream((Object[]) a.getArray()).map(Object::toString).toList();
            else if (v != null && "jsonb".equals(meta.getColumnTypeName(i)))
              v = new Json().read(v.toString());
            row.put(meta.getColumnLabel(i), v);
          }
          return row;
        },
        args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = rows(sql, args);
    if (rows.isEmpty()) throw DomainError.missing("Record");
    return rows.getFirst();
  }

  public long count(String sql, Object... args) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
  }

  public UUID uuid(Object value) {
    return UUID.fromString(value.toString());
  }
}
