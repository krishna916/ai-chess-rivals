import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import { MatchStatusLabel } from "./MatchStatusLabel";

describe("MatchStatusLabel", () => {
  afterEach(cleanup);

  it("shows a stopped match without implying it is live", () => {
    useMatchViewerStore.setState({ matchStatus: "STOPPED", moveCount: 31 });

    render(<MatchStatusLabel />);

    expect(screen.getByText("Stopped")).toBeInTheDocument();
    expect(screen.getByText("Move 31")).toBeInTheDocument();
    expect(screen.queryByText("Live")).not.toBeInTheDocument();
  });
});
