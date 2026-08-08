package dev.krishnamurti.ai_chess_rivals.ai.personality;

public record PersonalityRosterItem(
    String key, String displayName, String description, String avatarRef) {

  static PersonalityRosterItem from(PersonalityEntity entity) {
    return new PersonalityRosterItem(
        entity.personalityKey(), entity.displayName(), entity.description(), entity.avatarRef());
  }
}
