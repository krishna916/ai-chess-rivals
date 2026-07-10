package dev.krishnamurti.ai_chess_rivals.game.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Strongly-typed configuration properties for match engine orchestration. */
@ConfigurationProperties(prefix = "app.game")
@Validated
public record GameProperties(
    @Min(1) @Max(60_000) int moveThinkTimeMillis, @Min(1) @Max(1_000) int maxPlies) {}
