package com.lotline.shared;

import java.util.Map;

public class DomainError extends RuntimeException {
  public final int status;
  public final String code;
  public final Object details;

  public DomainError(int status, String code, String message) {
    this(status, code, message, Map.of());
  }

  public DomainError(int status, String code, String message, Object details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }

  public static DomainError invalid(String code, String message) {
    return new DomainError(422, code, message);
  }

  public static DomainError forbidden() {
    return new DomainError(
        403, "SCOPE_DENIED", "Your membership does not permit this action or site.");
  }

  public static DomainError missing(String type) {
    return new DomainError(404, "NOT_FOUND", type + " is not available in your workspace scope.");
  }
}
