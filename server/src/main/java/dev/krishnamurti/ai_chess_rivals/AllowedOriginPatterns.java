package dev.krishnamurti.ai_chess_rivals;

public final class AllowedOriginPatterns {

  public static final String LOCALHOST_HTTP = "http://localhost:[*]";
  public static final String LOCALHOST_HTTPS = "https://localhost:[*]";
  public static final String SUBDOMAIN_HTTP = "http://*.krishnamurti.dev:[*]";
  public static final String SUBDOMAIN_HTTPS = "https://*.krishnamurti.dev:[*]";

  private AllowedOriginPatterns() {}
}
