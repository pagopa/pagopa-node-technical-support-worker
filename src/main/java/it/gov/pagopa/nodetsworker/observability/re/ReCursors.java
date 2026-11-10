package it.gov.pagopa.nodetsworker.observability.re;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed random handles. Only bounded, short-lived seek metadata lives in memory; never source keys,
 * events or payloads. Restart/another replica invalidates handles (400), not the retained data.
 */
@ApplicationScoped
public class ReCursors {
  private final SecureRandom random = new SecureRandom();
  private final byte[] key = new byte[32];
  private final Map<String, Entry> entries = new HashMap<>();

  public ReCursors() {
    random.nextBytes(key);
  }

  synchronized String issue(Entry entry, Instant now) {
    entries.entrySet().removeIf(item -> !item.getValue().expires().isAfter(now));
    if (entries.size() >= 2048) {
      throw ReErrors.error(429, "RE_THROTTLED", "Limite consultazioni RE raggiunto.");
    }
    byte[] nonce = new byte[24];
    random.nextBytes(nonce);
    String handle = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    entries.put(handle, entry);
    return handle + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(handle));
  }

  synchronized Entry resolve(String cursor, String binding, Instant now) {
    if (cursor == null || !cursor.matches("[A-Za-z0-9_-]{32}\\.[A-Za-z0-9_-]{43}")) {
      throw ReErrors.invalid();
    }
    String handle = cursor.substring(0, 32);
    byte[] supplied;
    try {
      supplied = Base64.getUrlDecoder().decode(cursor.substring(33));
    } catch (IllegalArgumentException invalid) {
      throw ReErrors.invalid();
    }
    if (!Base64.getUrlEncoder().withoutPadding().encodeToString(supplied).equals(cursor.substring(33))
        || !MessageDigest.isEqual(sign(handle), supplied)) {
      throw ReErrors.invalid();
    }
    Entry entry = entries.get(handle);
    if (entry == null || !entry.expires().isAfter(now) || !entry.binding().equals(binding)) {
      throw ReErrors.invalid();
    }
    return entry;
  }

  private byte[] sign(String handle) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(handle.getBytes(StandardCharsets.US_ASCII));
    } catch (GeneralSecurityException unavailable) {
      throw ReErrors.config();
    }
  }

  record Entry(String binding, Instant from, Instant to, Instant watermark, Instant expires,
               Instant afterTime, String afterId, int count, boolean clipped) { }
}
