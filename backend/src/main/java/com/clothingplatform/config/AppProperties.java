package com.clothingplatform.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for the "app.*" section of application.yml. All secrets come from environment variables. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String environment, Cors cors, Jwt jwt) {
    public record Cors(List<String> allowedOrigins) {}
    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}
}
