package dev.krishnamurti.ai_chess_rivals.ai.personality;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PersonalityService {

  private final PersonalityRepository personalityRepository;

  PersonalityService(PersonalityRepository personalityRepository) {
    this.personalityRepository = personalityRepository;
  }

  List<PersonalityRosterItem> listSelectable() {
    return personalityRepository
        .findAllBySystemTrueAndActiveTrueOrderByDisplayOrderAscPersonalityKeyAsc()
        .stream()
        .filter(PersonalityEntity::selectableSystem)
        .map(PersonalityRosterItem::from)
        .toList();
  }

  public PersonalityPromptProfile requirePromptProfile(String personalityKey) {
    if (personalityKey == null || personalityKey.isBlank()) {
      throw new IllegalArgumentException("personalityKey must not be blank");
    }
    return personalityRepository
        .findByPersonalityKeyAndSystemTrueAndActiveTrue(personalityKey)
        .map(PersonalityPromptProfile::from)
        .orElseThrow(
            () ->
                new IllegalArgumentException("Unknown selectable personality: " + personalityKey));
  }
}
