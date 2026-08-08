package dev.krishnamurti.ai_chess_rivals.ai.personality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalityServiceTest {

  @Mock private PersonalityRepository personalityRepository;

  @Test
  void listsOnlyActiveSystemPersonalitiesInRepositoryOrder() {
    PersonalityService service = new PersonalityService(personalityRepository);
    when(personalityRepository.findAllByOrderByDisplayOrderAscPersonalityKeyAsc())
        .thenReturn(
            List.of(
                personality("archived", "Archived", 5, true, false),
                personality("custom", "Custom", 10, false, true),
                personality("alpha", "Alpha", 20, true, true),
                personality("zeta", "Zeta", 20, true, true)));

    assertThat(service.listSelectable())
        .containsExactly(
            new PersonalityRosterItem("alpha", "Alpha", "Alpha description", "/avatars/alpha.svg"),
            new PersonalityRosterItem("zeta", "Zeta", "Zeta description", "/avatars/zeta.svg"));

    verify(personalityRepository).findAllByOrderByDisplayOrderAscPersonalityKeyAsc();
  }

  @Test
  void returnsEmptyRosterWhenRepositoryHasNoRecords() {
    PersonalityService service = new PersonalityService(personalityRepository);
    when(personalityRepository.findAllByOrderByDisplayOrderAscPersonalityKeyAsc())
        .thenReturn(List.of());

    assertThat(service.listSelectable()).isEmpty();
  }

  private static PersonalityEntity personality(
      String key, String displayName, int displayOrder, boolean system, boolean active) {
    return new PersonalityEntity(
        key,
        displayName,
        displayName + " description",
        "Competitive, concise, and character-specific prompt traits.",
        new BigDecimal("0.650"),
        "Dry, confident speaking style.",
        "Keep banter PG-13; no slurs, sexual content, threats, or targeted abuse.",
        "/avatars/" + key + ".svg",
        displayOrder,
        system,
        active);
  }
}
