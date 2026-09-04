import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

vi.mock("./pages/MatchViewerPage", () => ({
  MatchViewerPage: () => <h1>Read-only Match Viewer</h1>,
}));
vi.mock("./pages/AdminPage", () => ({
  AdminPage: () => <h1>Locked Owner Controls</h1>,
}));

describe("App routes", () => {
  afterEach(() => {
    cleanup();
    window.location.hash = "";
  });

  it("routes the public hash root to the read-only viewer", () => {
    window.location.hash = "#/";
    render(<App />);
    expect(
      screen.getByRole("heading", { name: "Read-only Match Viewer" }),
    ).toBeVisible();
  });

  it("routes #/admin to the locked owner page", () => {
    window.location.hash = "#/admin";
    render(<App />);
    expect(
      screen.getByRole("heading", { name: "Locked Owner Controls" }),
    ).toBeVisible();
  });
});
