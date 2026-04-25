import { act, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import OfficerDashboardPage from "./OfficerDashboardPage";

const listOfficerApps = vi.fn();

vi.mock("../apiClient", () => ({
  subscribeNotificationStream: () => ({ supported: false, close: () => {} }),
  api: {
    listOfficerApps: (...args) => listOfficerApps(...args),
  },
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/officer/dashboard"]}>
      <Routes>
        <Route path="/officer/dashboard" element={<OfficerDashboardPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("OfficerDashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, "setInterval").mockImplementation(() => 1);
    vi.spyOn(window, "clearInterval").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("polls officer applications without manual refresh", async () => {
    listOfficerApps
      .mockResolvedValueOnce([
        {
          id: 11,
          referenceNumber: "REF-11",
          businessName: "New Operator App",
          statusLabel: "Submitted",
          internalStatus: "APPLICATION_RECEIVED",
          submissionRound: 1,
          lastModifiedAt: "2026-04-25T09:00:00Z",
        },
      ])
      .mockResolvedValueOnce([
        {
          id: 11,
          referenceNumber: "REF-11",
          businessName: "New Operator App",
          statusLabel: "Under Review",
          internalStatus: "UNDER_REVIEW",
          submissionRound: 1,
          lastModifiedAt: "2026-04-25T09:01:00Z",
        },
      ]);

    renderPage();

    await waitFor(() => expect(screen.getByText("Officer Dashboard")).toBeInTheDocument());
    const intervalCallback = window.setInterval.mock.calls[0]?.[0];
    expect(typeof intervalCallback).toBe("function");

    await act(async () => {
      await intervalCallback();
    });

    await waitFor(() => expect(listOfficerApps).toHaveBeenCalledTimes(2));
  });
});
