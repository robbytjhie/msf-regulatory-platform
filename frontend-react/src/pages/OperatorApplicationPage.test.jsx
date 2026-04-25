import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OperatorApplicationPage from "./OperatorApplicationPage";

const getOperatorApplication = vi.fn();
const getFlaggedItems = vi.fn();
const getOperatorDocumentStatuses = vi.fn();

vi.mock("../apiClient", () => ({
  api: {
    getOperatorApplication: (...args) => getOperatorApplication(...args),
    getFlaggedItems: (...args) => getFlaggedItems(...args),
    getOperatorDocumentStatuses: (...args) => getOperatorDocumentStatuses(...args),
  },
}));

const baseApp = {
  id: 7,
  referenceNumber: "REF-7",
  businessName: "Test Biz",
  statusLabel: "Submitted",
  submissionRound: 1,
  officerComments: [],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/operator/applications/7"]}>
      <Routes>
        <Route path="/operator/applications/:id" element={<OperatorApplicationPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("OperatorApplicationPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getOperatorApplication.mockResolvedValue({ ...baseApp, documents: [] });
    getFlaggedItems.mockResolvedValue([]);
    getOperatorDocumentStatuses.mockImplementation(() =>
      Promise.resolve([
        {
          id: 1,
          originalFileName: "doc.pdf",
          documentCategory: "Permit",
          aiVerificationStatus: "PROCESSING",
          aiVerificationNotes: "working",
        },
      ]),
    );
  });

  it("shows live document poll hint while AI verification is pending", async () => {
    getOperatorApplication.mockResolvedValue({
      ...baseApp,
      documents: [
        {
          id: 1,
          originalFileName: "doc.pdf",
          documentCategory: "Permit",
          aiVerificationStatus: "PENDING",
          aiVerificationNotes: "queued",
        },
      ],
    });

    renderPage();

    await waitFor(() => expect(getOperatorApplication).toHaveBeenCalledWith("7"));

    await waitFor(() => {
      expect(screen.getByText(/Live updates/i)).toBeInTheDocument();
    });

    await waitFor(() => expect(getOperatorDocumentStatuses).toHaveBeenCalled());
    await waitFor(() => {
      expect(screen.getByText(/Last updated/i)).toBeInTheDocument();
    });
  });

  it("hides live poll hint when all documents are finished", async () => {
    getOperatorApplication.mockResolvedValue({
      ...baseApp,
      documents: [
        {
          id: 1,
          originalFileName: "done.pdf",
          documentCategory: "Permit",
          aiVerificationStatus: "PASSED",
          aiVerificationNotes: "ok",
        },
      ],
    });

    renderPage();

    await waitFor(() => expect(getOperatorApplication).toHaveBeenCalled());

    expect(screen.queryByText(/Live updates/i)).not.toBeInTheDocument();
    expect(getOperatorDocumentStatuses).not.toHaveBeenCalled();
  });
});
