package dev.krishnamurti.ai_chess_rivals.game.web;

import jakarta.validation.constraints.NotBlank;

public record StartMatchRequest(
    @NotBlank(message = "whitePersonalityKey is required") String whitePersonalityKey,
    @NotBlank(message = "blackPersonalityKey is required") String blackPersonalityKey) {}
