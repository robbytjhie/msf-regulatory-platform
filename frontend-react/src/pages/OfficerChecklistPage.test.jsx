import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OfficerChecklistPage from "./OfficerChecklistPage";

const getChecklist = vi.fn();
const getOfficerApplication = vi.fn();

vi.mock("../apiClient", () => ({
  api: {
    getChecklist: (...args) => getChecklist(...args),
    getOfficerApplication: (...args) => getOfficerApplication(...args),
  },
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/officer/applications/9/checklist"]}>
      <Routes>
        <Route path="/officer/applications/:id/checklist" element={<OfficerChecklistPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("OfficerChecklistPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("locks checklist controls when all items resolved and app is pending approval", async () => {
    getChecklist.mockResolvedValue([
      { id: 1, itemCode: "C1", itemTitle: "Fire Exit", status: "SATISFACTORY", officerComment: "ok" },
      { id: 2, itemCode: "C2", itemTitle: "Electrical", status: "RESOLVED", officerComment: "resolved" },
    ]);
    getOfficerApplication.mockResolvedValue({ id: 9, internalStatus: "PENDING_APPROVAL" });

    renderPage();

    await waitFor(() => expect(screen.getByText("Site Checklist")).toBeInTheDocument());

    const allComboboxes = screen.getAllByRole("combobox");
    allComboboxes.forEach((el) => expect(el).toBeDisabled());

    const saveDraftBtn = screen.getByRole("button", { name: "Save Draft" });
    const submitBtn = screen.getByRole("button", { name: "Submit Checklist" });
    expect(saveDraftBtn).toBeDisabled();
    expect(submitBtn).toBeDisabled();
    expect(screen.getByText(/Checklist is locked/i)).toBeInTheDocument();
  });
});
