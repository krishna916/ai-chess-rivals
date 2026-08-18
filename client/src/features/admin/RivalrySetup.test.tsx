import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PersonalityRosterItem } from "@/types/match";
import { RivalrySetup } from "./RivalrySetup";

const roster: PersonalityRosterItem[] = [
  { key: "a", displayName: "Alpha", description: "Bold.", avatarRef: null },
  { key: "b", displayName: "Beta", description: "Precise.", avatarRef: null },
  { key: "c", displayName: "Gamma", description: "Playful.", avatarRef: null },
];

describe("RivalrySetup", () => {
  afterEach(cleanup);

  it("renders selectable personalities and emits changes", () => {
    const onWhiteChange = vi.fn();
    const onBlackChange = vi.fn();
    const onRandomize = vi.fn();
    render(
      <RivalrySetup
        roster={roster}
        whitePersonalityKey="a"
        blackPersonalityKey="b"
        disabled={false}
        onWhiteChange={onWhiteChange}
        onBlackChange={onBlackChange}
        onRandomize={onRandomize}
      />,
    );

    const white = screen.getByLabelText("White personality");
    const black = screen.getByLabelText("Black personality");
    expect(white).toHaveTextContent("Alpha");
    expect(white).toHaveTextContent("Beta");
    expect(white).toHaveTextContent("Gamma");
    expect(white.querySelector('option[value="b"]')).toBeDisabled();
    expect(black.querySelector('option[value="a"]')).toBeDisabled();
    expect(screen.getByText("Bold.")).toBeVisible();
    expect(screen.getByText("Precise.")).toBeVisible();

    fireEvent.change(white, { target: { value: "c" } });
    fireEvent.change(black, { target: { value: "a" } });
    fireEvent.click(screen.getByRole("button", { name: "Randomize Rivalry" }));

    expect(onWhiteChange).toHaveBeenCalledWith("c");
    expect(onBlackChange).toHaveBeenCalledWith("a");
    expect(onRandomize).toHaveBeenCalledTimes(1);
  });

  it("disables both selectors and randomization", () => {
    render(
      <RivalrySetup
        roster={roster}
        whitePersonalityKey="a"
        blackPersonalityKey="b"
        disabled
        onWhiteChange={vi.fn()}
        onBlackChange={vi.fn()}
        onRandomize={vi.fn()}
      />,
    );

    expect(screen.getAllByLabelText("White personality")[0]).toBeDisabled();
    expect(screen.getAllByLabelText("Black personality")[0]).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Randomize Rivalry" }),
    ).toBeDisabled();
  });
});
