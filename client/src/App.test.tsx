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
  afterEach(cleanup);

  it("routes the public root to the read-only viewer", () => {
    window.history.pushState({}, "", "/");
    render(<App />);
    expect(
      screen.getByRole("heading", { name: "Read-only Match Viewer" }),
    ).toBeVisible();
  });

  it("routes /admin to the locked owner page", () => {
    window.history.pushState({}, "", "/admin");
    render(<App />);
    expect(
      screen.getByRole("heading", { name: "Locked Owner Controls" }),
    ).toBeVisible();
  });
});
