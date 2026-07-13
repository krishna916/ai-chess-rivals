import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { OwnerTokenForm } from "./OwnerTokenForm";

describe("OwnerTokenForm", () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(cleanup);

  it("rejects blank input and explains session-only storage", () => {
    const onUnlock = vi.fn();
    render(<OwnerTokenForm onUnlock={onUnlock} />);

    expect(screen.getByLabelText("Owner control token")).toHaveAttribute(
      "type",
      "password",
    );
    expect(
      screen.getByText("Stored only for this browser session."),
    ).toBeVisible();
    fireEvent.change(screen.getByLabelText("Owner control token"), {
      target: { value: "   " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Unlock Controls" }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Enter an owner control token.",
    );
    expect(onUnlock).not.toHaveBeenCalled();
  });

  it("trims, stores, and unlocks without a validation request", () => {
    const onUnlock = vi.fn();
    render(<OwnerTokenForm onUnlock={onUnlock} />);

    fireEvent.change(screen.getByLabelText("Owner control token"), {
      target: { value: "  owner-token  " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Unlock Controls" }));

    expect(sessionStorage.getItem("ownerControlToken")).toBe("owner-token");
    expect(onUnlock).toHaveBeenCalledWith("owner-token");
  });
});
