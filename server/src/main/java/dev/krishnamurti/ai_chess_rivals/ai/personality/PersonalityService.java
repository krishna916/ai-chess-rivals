package dev.krishnamurti.ai_chess_rivals.ai.personality;

import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonality;
import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonalityCatalog;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PersonalityService implements SelectablePersonalityCatalog {

  private final PersonalityRepository personalityRepository;

  PersonalityService(PersonalityRepository personalityRepository) {
    this.personalityRepository = personalityRepository;
  }

  List<PersonalityRosterItem> listSelectable() {
    long startedAtNanos = System.nanoTime();
    log.info("event=db.started operation=personality_roster");
    try {
      List<PersonalityRosterItem> personalities =
          personalityRepository
              .findAllBySystemTrueAndActiveTrueOrderByDisplayOrderAscPersonalityKeyAsc()
              .stream()
              .filter(PersonalityEntity::selectableSystem)
              .map(PersonalityRosterItem::from)
              .toList();
      log.info(
          "event=db.completed operation=personality_roster outcome=success resultCount={} durationMs={}",
          personalities.size(),
          elapsedMillis(startedAtNanos));
      return personalities;
    } catch (RuntimeException exception) {
      log.info(
          "event=db.failed operation=personality_roster exceptionType={} durationMs={}",
          exception.getClass().getSimpleName(),
          elapsedMillis(startedAtNanos));
      throw exception;
    }
  }

  @Override
  public Optional<SelectablePersonality> findSelectable(String personalityKey) {
    if (personalityKey == null || personalityKey.isBlank()) {
      return Optional.empty();
    }
    long startedAtNanos = System.nanoTime();
    log.info("event=db.started operation=personality_lookup");
    try {
      Optional<SelectablePersonality> result =
          personalityRepository
              .findByPersonalityKeyAndSystemTrueAndActiveTrue(personalityKey)
              .map(
                  entity ->
                      new SelectablePersonality(entity.personalityKey(), entity.displayName()));
      log.info(
          "event=db.completed operation=personality_lookup outcome={} durationMs={}",
          result.isPresent() ? "found" : "not_found",
          elapsedMillis(startedAtNanos));
      return result;
    } catch (RuntimeException exception) {
      log.info(
          "event=db.failed operation=personality_lookup exceptionType={} durationMs={}",
          exception.getClass().getSimpleName(),
          elapsedMillis(startedAtNanos));
      throw exception;
    }
  }

  public PersonalityPromptProfile requirePromptProfile(String personalityKey) {
    if (personalityKey == null || personalityKey.isBlank()) {
      throw new IllegalArgumentException("personalityKey must not be blank");
    }
    long startedAtNanos = System.nanoTime();
    log.info("event=db.started operation=personality_prompt_profile");
    try {
      Optional<PersonalityPromptProfile> result =
          personalityRepository
              .findByPersonalityKeyAndSystemTrueAndActiveTrue(personalityKey)
              .map(PersonalityPromptProfile::from);
      log.info(
          "event=db.completed operation=personality_prompt_profile outcome={} durationMs={}",
          result.isPresent() ? "found" : "not_found",
          elapsedMillis(startedAtNanos));
      return result.orElseThrow(
          () -> new IllegalArgumentException("Unknown selectable personality: " + personalityKey));
    } catch (RuntimeException exception) {
      if (!(exception instanceof IllegalArgumentException)) {
        log.info(
            "event=db.failed operation=personality_prompt_profile exceptionType={} durationMs={}",
            exception.getClass().getSimpleName(),
            elapsedMillis(startedAtNanos));
      }
      throw exception;
    }
  }

  private static long elapsedMillis(long startedAtNanos) {
    return (System.nanoTime() - startedAtNanos) / 1_000_000L;
  }
}
