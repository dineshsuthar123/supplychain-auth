package com.lotline.identity;

import com.lotline.shared.DomainError;
import java.util.*;

public record Member(
    UUID userId,
    UUID sessionId,
    UUID workspaceId,
    UUID membershipId,
    UUID organizationId,
    String role,
    boolean recallManager,
    Set<UUID> sites,
    boolean networkRead)
    implements java.security.Principal {
  @Override
  public String getName() {
    return userId.toString();
  }

  public boolean owner() {
    return "OWNER".equals(role);
  }

  public boolean network() {
    return owner() || recallManager || networkRead;
  }

  public void write() {
    if ("AUDITOR".equals(role)) throw DomainError.forbidden();
  }

  public void administer() {
    if (!owner()) throw DomainError.forbidden();
  }

  public void manageRecall() {
    if (!network() || "AUDITOR".equals(role)) throw DomainError.forbidden();
  }
}
