package dev.krishnamurti.ai_chess_rivals.game.websocket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.websocket")
public record WebSocketProperties(@NotEmpty List<@NotBlank String> allowedOrigins) {

  public WebSocketProperties {
    allowedOrigins = List.copyOf(allowedOrigins);
  }
}
