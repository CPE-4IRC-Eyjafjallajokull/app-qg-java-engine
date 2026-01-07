package cpe.qg.engine.sdmis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cpe.qg.engine.auth.AuthStrategy;
import cpe.qg.engine.config.SdmisApiConfig;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** SDMIS API client with pluggable authentication strategy. */
public final class SdmisApiClient {

  private final SdmisApiConfig config;
  private final AuthStrategy authStrategy;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public SdmisApiClient(SdmisApiConfig config, AuthStrategy authStrategy) {
    this(config, authStrategy, HttpClient.newHttpClient(), new ObjectMapper());
  }

  public SdmisApiClient(
      SdmisApiConfig config,
      AuthStrategy authStrategy,
      HttpClient httpClient,
      ObjectMapper objectMapper) {
    this.config = Objects.requireNonNull(config, "SDMIS config is required");
    this.authStrategy = Objects.requireNonNull(authStrategy, "Auth strategy is required");
    this.httpClient = Objects.requireNonNull(httpClient, "HttpClient is required");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper is required");
  }

  public HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return send("GET", path, null, null);
  }

  public HttpResponse<String> delete(String path) throws IOException, InterruptedException {
    return send("DELETE", path, null, null);
  }

  public HttpResponse<String> postJson(String path, Object payload)
      throws IOException, InterruptedException {
    return sendJson("POST", path, payload);
  }

  public HttpResponse<String> putJson(String path, Object payload)
      throws IOException, InterruptedException {
    return sendJson("PUT", path, payload);
  }

  public <T> T getJson(String path, Class<T> responseType)
      throws IOException, InterruptedException {
    HttpResponse<String> response = get(path);
    ensureSuccess(response);
    return objectMapper.readValue(response.body(), responseType);
  }

  public <T> List<T> getJsonList(String path, TypeReference<List<T>> responseType)
      throws IOException, InterruptedException {
    HttpResponse<String> response = get(path);
    ensureSuccess(response);
    return objectMapper.readValue(response.body(), responseType);
  }

  public <T> T postJson(String path, Object payload, Class<T> responseType)
      throws IOException, InterruptedException {
    HttpResponse<String> response = postJson(path, payload);
    ensureSuccess(response);
    return objectMapper.readValue(response.body(), responseType);
  }

  private HttpResponse<String> sendJson(String method, String path, Object payload)
      throws IOException, InterruptedException {
    String json = toJson(payload);
    return send(method, path, json, "application/json");
  }

  private HttpResponse<String> send(String method, String path, String body, String contentType)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(config.baseUri(path))
            .timeout(Duration.ofMillis(config.timeoutMs()))
            .header("Accept", "application/json");

    authStrategy.apply(builder);

    if (body == null) {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      builder.header("Content-Type", contentType);
      builder.method(method, HttpRequest.BodyPublishers.ofString(body));
    }

    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private void ensureSuccess(HttpResponse<String> response) {
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      throw new IllegalStateException("SDMIS API call failed (status=%s)".formatted(status));
    }
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize payload to JSON", e);
    }
  }
}
