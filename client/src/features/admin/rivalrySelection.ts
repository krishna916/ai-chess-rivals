import type { PersonalityRosterItem, StartMatchRequest } from "@/types/match";

export function randomizeRivalry(
  roster: PersonalityRosterItem[],
  random: () => number = Math.random,
): StartMatchRequest {
  if (roster.length < 2) {
    throw new Error("At least two personalities are required");
  }

  const whiteIndex = Math.floor(random() * roster.length);
  const compressedBlackIndex = Math.floor(random() * (roster.length - 1));
  const blackIndex =
    compressedBlackIndex >= whiteIndex
      ? compressedBlackIndex + 1
      : compressedBlackIndex;

  return {
    whitePersonalityKey: roster[whiteIndex].key,
    blackPersonalityKey: roster[blackIndex].key,
  };
}
