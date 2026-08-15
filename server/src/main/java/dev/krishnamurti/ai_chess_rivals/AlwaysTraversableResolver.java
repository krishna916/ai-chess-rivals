package dev.krishnamurti.ai_chess_rivals;

import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;
import java.lang.annotation.ElementType;

final class AlwaysTraversableResolver implements TraversableResolver {

  static final AlwaysTraversableResolver INSTANCE = new AlwaysTraversableResolver();

  private AlwaysTraversableResolver() {}

  @Override
  public boolean isReachable(
      Object traversableObject,
      Path.Node traversableProperty,
      Class<?> rootBeanType,
      Path pathToTraversableObject,
      ElementType elementType) {
    return true;
  }

  @Override
  public boolean isCascadable(
      Object traversableObject,
      Path.Node traversableProperty,
      Class<?> rootBeanType,
      Path pathToTraversableObject,
      ElementType elementType) {
    return true;
  }
}
