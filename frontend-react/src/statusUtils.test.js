import { describe, expect, it } from "vitest";
import {
  pendingOwnerByInternalStatus,
  pendingOwnerByStatusLabel,
  statusClass,
} from "./statusUtils";

describe("statusUtils", () => {
  it("maps internal status to pending owner", () => {
    expect(pendingOwnerByInternalStatus("APPLICATION_RECEIVED")).toBe("Pending Officer Action");
    expect(pendingOwnerByInternalStatus("PENDING_PRE_SITE_RESUBMISSION")).toBe("Pending Operator Action");
    expect(pendingOwnerByInternalStatus("UNDER_REVIEW")).toBe("Pending Officer Action");
  });

  it("maps status labels for operator wording", () => {
    expect(pendingOwnerByStatusLabel("Pending Pre-Site Resubmission")).toBe("Pending Your Action");
    expect(pendingOwnerByStatusLabel("Pending Post-Site Resubmission")).toBe("Pending Your Action");
    expect(pendingOwnerByStatusLabel("Approved")).toBe("Approved");
    expect(pendingOwnerByStatusLabel("Under Review")).toBe("In Progress");
  });

  it("returns fallback badge class for unknown labels", () => {
    expect(statusClass("Unknown Status")).toBe("badge-gray");
  });
});
