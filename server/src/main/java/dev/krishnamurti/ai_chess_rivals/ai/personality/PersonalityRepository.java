package dev.krishnamurti.ai_chess_rivals.ai.personality;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PersonalityRepository extends JpaRepository<PersonalityEntity, Long> {

  List<PersonalityEntity> findAllByOrderByDisplayOrderAscPersonalityKeyAsc();
}
