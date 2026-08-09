package dev.krishnamurti.ai_chess_rivals.ai.personality;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

interface PersonalityRepository extends Repository<PersonalityEntity, Long> {

  List<PersonalityEntity> findAllBySystemTrueAndActiveTrueOrderByDisplayOrderAscPersonalityKeyAsc();

  Optional<PersonalityEntity> findByPersonalityKeyAndSystemTrueAndActiveTrue(String personalityKey);
}
