package cpe.qg.engine.config;

import java.net.URI;
import java.util.Objects;

/** Immutable SDMIS API settings loaded from the environment. */
public record SdmisApiConfig(String baseUrl, long timeoutMs) {

  public SdmisApiConfig {
    Objects.requireNonNull(baseUrl, "SDMIS API baseUrl is required");
    if (timeoutMs <= 0) {
      throw new IllegalArgumentException("timeoutMs must be positive");
    }
  }

  public URI baseUri(String path) {
    String normalizedBase = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    String normalizedPath = path == null ? "" : path;
    normalizedPath = normalizedPath.startsWith("/") ? normalizedPath.substring(1) : normalizedPath;
    return URI.create(normalizedBase + normalizedPath);
  }
}
