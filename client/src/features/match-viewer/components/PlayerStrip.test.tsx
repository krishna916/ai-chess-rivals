import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import { PlayerStrip } from "./PlayerStrip";

describe("PlayerStrip", () => {
  afterEach(cleanup);

  beforeEach(() => {
    useMatchViewerStore.setState({
      matchStatus: "IDLE",
      activeTurn: "WHITE",
      whitePersonality: undefined,
      blackPersonality: undefined,
    });
  });

  it("renders the authoritative white identity", () => {
    useMatchViewerStore.setState({
      whitePersonality: { key: "blaze", displayName: "Blaze" },
    });

    render(<PlayerStrip side="WHITE" />);

    expect(screen.getByText("Blaze")).toBeVisible();
  });

  it("renders the authoritative black identity", () => {
    useMatchViewerStore.setState({
      blackPersonality: { key: "vesper", displayName: "Vesper" },
    });

    render(<PlayerStrip side="BLACK" />);

    expect(screen.getByText("Vesper")).toBeVisible();
  });

  it("falls back to Stockfish names before a match", () => {
    render(
      <>
        <PlayerStrip side="WHITE" />
        <PlayerStrip side="BLACK" />
      </>,
    );

    expect(screen.getByText("Stockfish White")).toBeVisible();
    expect(screen.getByText("Stockfish Black")).toBeVisible();
  });

  it("keeps the thinking badge for the active side", () => {
    useMatchViewerStore.setState({
      matchStatus: "IN_PROGRESS",
      activeTurn: "WHITE",
      whitePersonality: { key: "blaze", displayName: "Blaze" },
    });

    render(<PlayerStrip side="WHITE" />);

    expect(screen.getByText("Blaze")).toBeVisible();
    expect(screen.getByText("Thinking...")).toBeVisible();
  });
});
