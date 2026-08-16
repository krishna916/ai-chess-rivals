package dev.krishnamurti.ai_chess_rivals.ai.personality;

import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonality;
import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonalityCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PersonalityService implements SelectablePersonalityCatalog {

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

  @Override
  public Optional<SelectablePersonality> findSelectable(String personalityKey) {
    if (personalityKey == null || personalityKey.isBlank()) {
      return Optional.empty();
    }
    return personalityRepository
        .findByPersonalityKeyAndSystemTrueAndActiveTrue(personalityKey)
        .map(entity -> new SelectablePersonality(entity.personalityKey(), entity.displayName()));
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
