package dev.krishnamurti.ai_chess_rivals.ai.personality;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
class PersonalityService {

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
}
