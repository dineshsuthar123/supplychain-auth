package com.lotline.identity;

import com.lotline.shared.DomainError;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;

@Component
public class AuthRateGate {
  private record Window(long minute, int attempts) {}

  private final LinkedHashMap<String, Window> windows = new LinkedHashMap<>(128, .75f, true);

  public synchronized void check(String account) {
    long minute = Instant.now().getEpochSecond() / 60;
    var previous = windows.get(account);
    int attempts = previous != null && previous.minute() == minute ? previous.attempts() + 1 : 1;
    if (attempts > 20)
      throw new DomainError(
          429,
          "AUTH_RATE_LIMIT",
          "Too many authentication attempts for this account. Wait one minute and try again.");
    windows.put(account, new Window(minute, attempts));
    if (windows.size() > 10000) windows.remove(windows.keySet().iterator().next());
  }
}
