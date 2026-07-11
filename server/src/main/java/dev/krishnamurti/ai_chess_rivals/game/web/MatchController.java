package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/match")
public class MatchController {

  private final MatchControlService matchControlService;

  public MatchController(MatchControlService matchControlService) {
    this.matchControlService = matchControlService;
  }

  @PostMapping("/start")
  public String startMatch() {
    return "start";
  }

  @PostMapping("/stop")
  public String stopMatch() {
    return "stop";
  }

  @GetMapping
  public String currentMatch() {
    return "current";
  }
}
