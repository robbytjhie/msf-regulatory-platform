import { act, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import OperatorDashboardPage from "./OperatorDashboardPage";

const listOperatorApps = vi.fn();

vi.mock("../apiClient", () => ({
  subscribeNotificationStream: () => ({ supported: false, close: () => {} }),
  api: {
    listOperatorApps: (...args) => listOperatorApps(...args),
  },
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/operator/dashboard"]}>
      <Routes>
        <Route path="/operator/dashboard" element={<OperatorDashboardPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("OperatorDashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, "setInterval").mockImplementation(() => 1);
    vi.spyOn(window, "clearInterval").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("polls operator applications without manual refresh", async () => {
    listOperatorApps
      .mockResolvedValueOnce([
        {
          id: 5,
          referenceNumber: "REF-5",
          businessName: "Sunshine Home",
          statusLabel: "Submitted",
          submissionRound: 1,
          lastModifiedAt: "2026-04-25T08:00:00Z",
        },
      ])
      .mockResolvedValueOnce([
        {
          id: 5,
          referenceNumber: "REF-5",
          businessName: "Sunshine Home",
          statusLabel: "Pending Pre-Site Resubmission",
          submissionRound: 1,
          lastModifiedAt: "2026-04-25T08:01:00Z",
        },
      ]);

    renderPage();

    await waitFor(() => expect(screen.getByText("Submitted")).toBeInTheDocument());
    const intervalCallback = window.setInterval.mock.calls[0]?.[0];
    expect(typeof intervalCallback).toBe("function");

    await act(async () => {
      await intervalCallback();
    });

    await waitFor(() => expect(listOperatorApps).toHaveBeenCalledTimes(2));
  });
});
