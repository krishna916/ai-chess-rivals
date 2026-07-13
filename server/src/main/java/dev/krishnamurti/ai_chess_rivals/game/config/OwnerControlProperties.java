package dev.krishnamurti.ai_chess_rivals.game.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Backend-only credential for the shared match control endpoints. */
@ConfigurationProperties(prefix = "app.owner")
@Validated
public record OwnerControlProperties(@NotBlank String controlToken) {}
