package com.lotline.catalog;

import com.lotline.identity.Member;
import com.lotline.shared.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
  public record Product(@NotBlank @Size(max = 160) String name, @NotBlank String baseUnit) {}

  private final Db db;
  private final CommandBus bus;

  public CatalogService(Db db, CommandBus bus) {
    this.db = db;
    this.bus = bus;
  }

  public List<Map<String, Object>> products(Member p) {
    return db.rows(
        "SELECT id,name,base_unit FROM products WHERE workspace_id=? ORDER BY name LIMIT 500",
        p.workspaceId());
  }

  public Map<String, Object> create(Member p, String key, Product r) {
    if ("PARTNER_OPERATOR".equals(p.role())) throw DomainError.forbidden();
    return bus.run(
        p,
        "PRODUCT_CREATED",
        key,
        r,
        null,
        c -> {
          if (!Set.of("unit", "kg", "g", "l", "ml").contains(r.baseUnit()))
            throw DomainError.invalid(
                "UNKNOWN_UNIT",
                "Choose unit, kg, g, l or ml. Transformations must keep the same product and base"
                    + " unit.");
          UUID id = UUID.randomUUID();
          db.update(
              "INSERT INTO products VALUES(?,?,?,?)", p.workspaceId(), id, r.name(), r.baseUnit());
          return Map.of("id", id.toString());
        });
  }
}
