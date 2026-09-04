package dev.krishnamurti.ai_chess_rivals.ai.personality;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/personalities")
@CrossOrigin(origins = "${APP_WEBSOCKET_ALLOWED_ORIGIN:http://localhost:5173}")
class PersonalityController {

  private final PersonalityService personalityService;

  PersonalityController(PersonalityService personalityService) {
    this.personalityService = personalityService;
  }

  @GetMapping
  public ResponseEntity<List<PersonalityRosterItem>> listPersonalities() {
    return ResponseEntity.ok(personalityService.listSelectable());
  }
}
