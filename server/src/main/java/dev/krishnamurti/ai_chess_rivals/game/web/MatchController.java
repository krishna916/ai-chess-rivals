package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.AllowedOriginPatterns;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/api/v1/match")
@CrossOrigin(
    origins = "${APP_WEBSOCKET_ALLOWED_ORIGIN:http://localhost:5173}",
    originPatterns = {
      AllowedOriginPatterns.LOCALHOST_HTTP,
      AllowedOriginPatterns.LOCALHOST_HTTPS,
      AllowedOriginPatterns.SUBDOMAIN_HTTP,
      AllowedOriginPatterns.SUBDOMAIN_HTTPS
    })
public class MatchController {

  private final MatchControlService matchControlService;

  public MatchController(MatchControlService matchControlService) {
    this.matchControlService = matchControlService;
  }

  @PostMapping("/start")
  public ResponseEntity<MatchResponse> startMatch(@Valid @RequestBody StartMatchRequest request) {
    log.info("event=controller.entered operation=match_start");
    long startedAtNanos = System.nanoTime();
    MatchSnapshot snapshot =
        matchControlService.startMatch(
            request.whitePersonalityKey(), request.blackPersonalityKey());
    log.info(
        "event=controller.completed operation=match_start outcome=accepted matchId={} whiteKey={} blackKey={} durationMs={}",
        snapshot.match().id(),
        snapshot.match().rivalry().whiteKey(),
        snapshot.match().rivalry().blackKey(),
        elapsedMillis(startedAtNanos));
    return ResponseEntity.accepted().body(MatchResponseMapper.map(snapshot));
  }

  @PostMapping("/stop")
  public ResponseEntity<MatchResponse> stopMatch() {
    log.info("event=controller.entered operation=match_stop");
    long startedAtNanos = System.nanoTime();
    MatchSnapshot snapshot = matchControlService.stopMatch();
    log.info(
        "event=controller.completed operation=match_stop outcome=accepted matchId={} durationMs={}",
        snapshot.match().id(),
        elapsedMillis(startedAtNanos));
    return ResponseEntity.accepted().body(MatchResponseMapper.map(snapshot));
  }

  @GetMapping
  public ResponseEntity<MatchResponse> currentMatch() {
    log.info("event=controller.entered operation=match_current");
    long startedAtNanos = System.nanoTime();
    MatchSnapshot snapshot = matchControlService.currentMatch();
    log.info(
        "event=controller.completed operation=match_current outcome=success matchId={} durationMs={}",
        snapshot.match().id(),
        elapsedMillis(startedAtNanos));
    return ResponseEntity.ok(MatchResponseMapper.map(snapshot));
  }

  private static long elapsedMillis(long startedAtNanos) {
    return (System.nanoTime() - startedAtNanos) / 1_000_000L;
  }
}
