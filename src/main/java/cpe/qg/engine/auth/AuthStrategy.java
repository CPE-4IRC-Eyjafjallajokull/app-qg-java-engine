package cpe.qg.engine.auth;

import java.net.http.HttpRequest;

/** Strategy interface for authenticating outbound HTTP requests. */
public interface AuthStrategy {

  void apply(HttpRequest.Builder builder);
}
