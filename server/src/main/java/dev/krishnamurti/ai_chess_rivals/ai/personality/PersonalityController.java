package dev.krishnamurti.ai_chess_rivals.ai.personality;

import dev.krishnamurti.ai_chess_rivals.AllowedOriginPatterns;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/api/v1/personalities")
@CrossOrigin(
    origins = "${APP_WEBSOCKET_ALLOWED_ORIGIN:http://localhost:5173}",
    originPatterns = {
      AllowedOriginPatterns.LOCALHOST_HTTP,
      AllowedOriginPatterns.LOCALHOST_HTTPS,
      AllowedOriginPatterns.SUBDOMAIN_HTTP,
      AllowedOriginPatterns.SUBDOMAIN_HTTPS
    })
class PersonalityController {

  private final PersonalityService personalityService;

  PersonalityController(PersonalityService personalityService) {
    this.personalityService = personalityService;
  }

  @GetMapping
  public ResponseEntity<List<PersonalityRosterItem>> listPersonalities() {
    log.info("event=controller.entered operation=personalities_list");
    long startedAtNanos = System.nanoTime();
    List<PersonalityRosterItem> personalities = personalityService.listSelectable();
    log.info(
        "event=controller.completed operation=personalities_list outcome=success resultCount={} durationMs={}",
        personalities.size(),
        (System.nanoTime() - startedAtNanos) / 1_000_000L);
    return ResponseEntity.ok(personalities);
  }
}
