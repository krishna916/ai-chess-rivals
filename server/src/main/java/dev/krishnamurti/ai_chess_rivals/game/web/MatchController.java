package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/match")
@CrossOrigin(origins = "http://localhost:5173")
public class MatchController {

  private final MatchControlService matchControlService;

  public MatchController(MatchControlService matchControlService) {
    this.matchControlService = matchControlService;
  }

  @PostMapping("/start")
  public ResponseEntity<MatchResponse> startMatch() {
    MatchSnapshot snapshot = matchControlService.startMatch();
    return ResponseEntity.accepted().body(MatchResponseMapper.map(snapshot));
  }

  @PostMapping("/stop")
  public ResponseEntity<MatchResponse> stopMatch() {
    MatchSnapshot snapshot = matchControlService.stopMatch();
    return ResponseEntity.accepted().body(MatchResponseMapper.map(snapshot));
  }

  @GetMapping
  public ResponseEntity<MatchResponse> currentMatch() {
    MatchSnapshot snapshot = matchControlService.currentMatch();
    return ResponseEntity.ok(MatchResponseMapper.map(snapshot));
  }
}
