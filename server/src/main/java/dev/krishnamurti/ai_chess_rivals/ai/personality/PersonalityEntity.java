package dev.krishnamurti.ai_chess_rivals.ai.personality;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "personality")
class PersonalityEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "personality_key", nullable = false, unique = true, length = 64)
  private String personalityKey;

  @Column(name = "display_name", nullable = false, length = 80)
  private String displayName;

  @Column(nullable = false, length = 280)
  private String description;

  @Column(name = "prompt_traits", nullable = false, columnDefinition = "text")
  private String promptTraits;

  @Column(name = "speaking_probability", nullable = false, precision = 4, scale = 3)
  private BigDecimal speakingProbability;

  @Column(name = "style_guidance", nullable = false, columnDefinition = "text")
  private String styleGuidance;

  @Column(name = "boundary_guidance", nullable = false, columnDefinition = "text")
  private String boundaryGuidance;

  @Column(name = "avatar_ref", length = 255)
  private String avatarRef;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "is_system", nullable = false)
  private boolean system;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  protected PersonalityEntity() {
    this.id = null;
  }

  PersonalityEntity(
      String personalityKey,
      String displayName,
      String description,
      String promptTraits,
      BigDecimal speakingProbability,
      String styleGuidance,
      String boundaryGuidance,
      String avatarRef,
      int displayOrder,
      boolean system,
      boolean active) {
    this.id = null;
    this.personalityKey = personalityKey;
    this.displayName = displayName;
    this.description = description;
    this.promptTraits = promptTraits;
    this.speakingProbability = speakingProbability;
    this.styleGuidance = styleGuidance;
    this.boundaryGuidance = boundaryGuidance;
    this.avatarRef = avatarRef;
    this.displayOrder = displayOrder;
    this.system = system;
    this.active = active;
  }

  Long id() {
    return id;
  }

  String personalityKey() {
    return personalityKey;
  }

  String displayName() {
    return displayName;
  }

  String description() {
    return description;
  }

  String promptTraits() {
    return promptTraits;
  }

  BigDecimal speakingProbability() {
    return speakingProbability;
  }

  String styleGuidance() {
    return styleGuidance;
  }

  String boundaryGuidance() {
    return boundaryGuidance;
  }

  String avatarRef() {
    return avatarRef;
  }

  int displayOrder() {
    return displayOrder;
  }

  boolean system() {
    return system;
  }

  boolean active() {
    return active;
  }

  boolean selectableSystem() {
    return system && active;
  }
}
