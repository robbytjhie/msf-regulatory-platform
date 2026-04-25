import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../apiClient";
import { statusClass } from "../statusUtils";

const transitionMap = {
  APPLICATION_RECEIVED: ["UNDER_REVIEW"],
  MANUAL_OFFICER_VALIDATION: ["PENDING_PRE_SITE_RESUBMISSION", "SITE_VISIT_SCHEDULED", "PENDING_APPROVAL", "REJECTED"],
  UNDER_REVIEW: ["PENDING_PRE_SITE_RESUBMISSION", "SITE_VISIT_SCHEDULED", "PENDING_APPROVAL", "REJECTED"],
  PRE_SITE_RESUBMITTED: ["UNDER_REVIEW", "SITE_VISIT_SCHEDULED"],
  SITE_VISIT_SCHEDULED: ["SITE_VISIT_DONE", "AWAITING_POST_SITE_CLARIFICATION", "PENDING_APPROVAL"],
  SITE_VISIT_DONE: ["AWAITING_POST_SITE_CLARIFICATION", "PENDING_APPROVAL"],
  AWAITING_POST_SITE_CLARIFICATION: ["PENDING_POST_SITE_RESUBMISSION"],
  POST_SITE_CLARIFICATION_RESUBMITTED: ["AWAITING_POST_SITE_CLARIFICATION", "PENDING_APPROVAL"],
  PENDING_APPROVAL: ["APPROVED", "REJECTED"],
};

/** Officer can open the site-visit checklist only in these workflow stages (backend enforces the same). */
const CHECKLIST_AVAILABLE_STATUSES = new Set(["SITE_VISIT_SCHEDULED", "SITE_VISIT_DONE"]);

const labelToInternalStatus = {
  "APPLICATION RECEIVED": "APPLICATION_RECEIVED",
  "MANUAL OFFICER VALIDATION": "MANUAL_OFFICER_VALIDATION",
  "UNDER REVIEW": "UNDER_REVIEW",
  "PENDING PRE-SITE RESUBMISSION": "PENDING_PRE_SITE_RESUBMISSION",
  "PRE-SITE RESUBMITTED": "PRE_SITE_RESUBMITTED",
  "SITE VISIT SCHEDULED": "SITE_VISIT_SCHEDULED",
  "SITE VISIT DONE": "SITE_VISIT_DONE",
  "AWAITING POST-SITE CLARIFICATION": "AWAITING_POST_SITE_CLARIFICATION",
  "PENDING POST-SITE RESUBMISSION": "PENDING_POST_SITE_RESUBMISSION",
  "POST-SITE CLARIFICATION RESUBMITTED": "POST_SITE_CLARIFICATION_RESUBMITTED",
  "PENDING APPROVAL": "PENDING_APPROVAL",
  APPROVED: "APPROVED",
  REJECTED: "REJECTED",
};

function normalizeInternalStatus(value) {
  if (!value) return "";
  const raw = String(value).trim();
  if (transitionMap[raw]) return raw;
  const underscored = raw.replace(/\s+/g, "_").toUpperCase();
  if (transitionMap[underscored]) return underscored;
  const labelKey = raw.replace(/_/g, " ").toUpperCase();
  return labelToInternalStatus[labelKey] || underscored;
}

function docAiBadgeClass(status) {
  if (status === "PASSED") return "badge-green";
  if (status === "FLAGGED" || status === "FAILED") return "badge-red";
  return "badge-amber";
}

function formatTs(value) {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString();
}

export default function OfficerApplicationPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [app, setApp] = useState(null);
  const [newStatus, setNewStatus] = useState("UNDER_REVIEW");
  const [comment, setComment] = useState("");
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [docPollActive, setDocPollActive] = useState(false);
  const [lastDocPollAt, setLastDocPollAt] = useState(null);
  const [nowTick, setNowTick] = useState(() => Date.now());
  const [requiredReturnDocIds, setRequiredReturnDocIds] = useState([]);

  useEffect(() => {
    api.getOfficerApplication(id).then((d) => {
      setApp(d);
      const defaultIssueDocIds = (d.documents || [])
        .filter((doc) => doc.aiVerificationStatus === "FLAGGED" || doc.aiVerificationStatus === "FAILED")
        .map((doc) => doc.id);
      setRequiredReturnDocIds(defaultIssueDocIds);
      const currentStatus = normalizeInternalStatus(d.internalStatus);
      const allowed = transitionMap[currentStatus] || [];
      setNewStatus(allowed[0] || currentStatus || "UNDER_REVIEW");
    }).catch((e) => setErr(e.message));
  }, [id]);

  useEffect(() => {
    if (!app?.internalStatus) return;
    const currentStatus = normalizeInternalStatus(app.internalStatus);
    const allowed = transitionMap[currentStatus] || [];
    if (!allowed.length) {
      setNewStatus(currentStatus);
      return;
    }
    if (!allowed.includes(newStatus)) {
      setNewStatus(allowed[0]);
    }
  }, [app?.internalStatus]);

  useEffect(() => {
    if (!app?.documents?.length) {
      setDocPollActive(false);
      return;
    }
    const pending = app.documents.some(
      (d) => d.aiVerificationStatus === "PENDING" || d.aiVerificationStatus === "PROCESSING"
    );
    setDocPollActive(pending);
  }, [app?.documents]);

  useEffect(() => {
    if (!docPollActive || !id) return;
    const run = async () => {
      try {
        const fresh = await api.getOfficerApplication(id);
        setLastDocPollAt(Date.now());
        setApp((prev) => (prev ? { ...prev, documents: fresh.documents } : prev));
        if (!fresh.documents?.some((d) => d.aiVerificationStatus === "PENDING" || d.aiVerificationStatus === "PROCESSING")) {
          setDocPollActive(false);
        }
      } catch {
        // Ignore polling blips; detail view remains usable.
      }
    };
    run();
    const timer = window.setInterval(run, 2000);
    return () => window.clearInterval(timer);
  }, [id, docPollActive]);

  useEffect(() => {
    if (!docPollActive) return;
    const t = window.setInterval(() => setNowTick(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [docPollActive]);

  const logout = () => {
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("user");
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    navigate("/login");
  };

  const submitFeedback = async () => {
    if (!window.confirm("Submit officer feedback and status update?")) return;
    try {
      setSubmitting(true);
      setErr("");
      setOk("");
      let targetStatus = newStatus;
      const currentStatus = normalizeInternalStatus(app.internalStatus);
      if (targetStatus === currentStatus) {
        const allowed = transitionMap[currentStatus] || [];
        const fallback = allowed.find((s) => s !== currentStatus);
        if (!fallback) {
          setErr("No valid status transition available from current state.");
          return;
        }
        targetStatus = fallback;
        setNewStatus(fallback);
      }
      const documentFixComments = requiredReturnDocIds.map((docId) => {
        const doc = app.documents?.find((d) => d.id === docId);
        return {
          commentText: `[Document Return Required] ${doc?.originalFileName || "Document"} (${doc?.documentCategory || "-"}) requires fixes. Refer to officer notes for reason.`,
          targetSection: "document",
          targetDocumentId: docId,
        };
      });
      const commentsPayload = [
        ...(comment ? [{ commentText: comment, targetSection: null, targetDocumentId: null }] : []),
        ...documentFixComments,
      ];
      const timelineNotes = commentsPayload[0]?.commentText?.trim() || null;
      const updated = await api.submitOfficerFeedback(id, {
        newStatus: targetStatus,
        statusNotes: timelineNotes,
        comments: commentsPayload,
      });
      setApp(updated);
      setComment("");
      setOk("Feedback submitted and status updated.");
    } catch (e) {
      setErr(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  const requireReturn = (doc) => {
    if (!doc?.id) return;
    const line = `Document "${doc.originalFileName}" requires fixes. Reason: `;
    setComment((prev) => (prev?.includes(line) ? prev : (prev ? `${prev}\n${line}` : line)));
    setRequiredReturnDocIds((prev) => (prev.includes(doc.id) ? prev : [...prev, doc.id]));
    setOk(`Marked "${doc.originalFileName}" as return required. Add your reason in the comment box.`);
  };

  if (!app) return <main className="app-shell">{err ? <p className="error">{err}</p> : <p>Loading...</p>}</main>;
  const currentStatus = normalizeInternalStatus(app.internalStatus);
  const allowedTransitions = transitionMap[currentStatus] || [];
  const checklistEnabled = CHECKLIST_AVAILABLE_STATUSES.has(currentStatus);

  return (
    <main className="app-shell">
      <header className="top">
        <h2>{app.businessName}</h2>
        <div className="top">
          <button type="button" className="btn secondary" onClick={() => navigate("/officer/dashboard")}>Back</button>
          <button
            type="button"
            className="btn secondary"
            disabled={!checklistEnabled}
            title={
              checklistEnabled
                ? "Open site-visit checklist"
                : "Checklist is available after you set status to Site Visit Scheduled (template items are created automatically)."
            }
            onClick={() => checklistEnabled && navigate(`/officer/applications/${id}/checklist`)}
          >
            Checklist
          </button>
          <button type="button" className="btn secondary" onClick={logout}>Logout</button>
        </div>
      </header>
      {!checklistEnabled ? (
        <p className="hint" style={{ marginTop: 0 }}>
          <strong>Site-visit checklist:</strong> use <em>Submit Feedback</em> to move this application to{" "}
          <code>SITE_VISIT_SCHEDULED</code>. The system then creates default checklist rows you can complete on the Checklist screen.
        </p>
      ) : null}
      <section className="card">
        <h3>{app.referenceNumber} <span className={`badge ${statusClass(app.statusLabel)}`}>{app.statusLabel}</span></h3>
        <p>Operator: {app.operatorName} ({app.operatorEmail})</p>
        {app.assignedOfficerName ? <p>Assigned officer: {app.assignedOfficerName}</p> : null}
        <p>Track: {app.licensingTrack || "-"}</p>
        <p>Submission Round #{app.submissionRound}</p>
        <p><strong>Final Outcome:</strong> {app.statusLabel === "Approved" || app.statusLabel === "Rejected" ? app.statusLabel : "In Progress"}</p>
        <p><strong>Current internal state:</strong> {currentStatus || app.internalStatus}</p>
        {app.statusHistory?.length ? (
          <>
            <h4>Shared Timeline</h4>
            <table>
              <thead><tr><th>When</th><th>From</th><th>To</th><th>Changed by</th><th>Notes</th></tr></thead>
              <tbody>
                {app.statusHistory.map((h, idx) => (
                  <tr key={`${h.changedAt || idx}-${h.toStatusLabel || idx}`}>
                    <td>{formatTs(h.changedAt)}</td>
                    <td>{h.fromStatusLabel || "Initial submission"}</td>
                    <td>{h.toStatusLabel || "-"}</td>
                    <td>{h.changedByName || "System"}</td>
                    <td>{h.notes || "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ) : null}

        <h4>Application details</h4>
        <dl className="detail-grid">
          <dt>Business type</dt>
          <dd>{app.businessType || "—"}</dd>
          <dt>Address</dt>
          <dd>{app.businessAddress || "—"}</dd>
          <dt>Contact phone</dt>
          <dd>{app.contactPhone || "—"}</dd>
          <dt>Activity</dt>
          <dd>{app.activityDescription || "—"}</dd>
          {app.submittedAt ? (
            <>
              <dt>Submitted</dt>
              <dd>{typeof app.submittedAt === "string" ? app.submittedAt : JSON.stringify(app.submittedAt)}</dd>
            </>
          ) : null}
        </dl>

        {app.documents?.length ? (
          <>
            <div className="doc-table-header">
              <h4>Submitted documents</h4>
              {docPollActive ? (
                <p className="doc-poll-meta">
                  <span className="live">Live updates</span>
                  {lastDocPollAt != null
                    ? ` · Last updated ${Math.max(0, Math.floor((nowTick - lastDocPollAt) / 1000))}s ago`
                    : " · syncing…"}
                </p>
              ) : null}
            </div>
            <div className="workflow-box">
              <p className="workflow-title">AI document verification (for officers)</p>
              <p className="workflow-line">
                <strong>PENDING / PROCESSING</strong> — not finished yet; refresh or wait for live updates.
              </p>
              <p className="workflow-line">
                <strong>PASSED</strong> — automated checks did not raise issues (still verify on your own).
              </p>
              <p className="workflow-line">
                <strong>FLAGGED</strong> — needs human review; read the <strong>Notes</strong> column for the system&apos;s reason.
              </p>
              <p className="workflow-line">
                <strong>FAILED</strong> — verification could not complete or document is unusable; use notes and request a resubmission if needed.
              </p>
            </div>
            <table>
              <thead><tr><th>No.</th><th>File</th><th>Category</th><th>AI status</th><th>Notes (why flagged, etc.)</th><th>Action</th></tr></thead>
              <tbody>
                {app.documents.map((d, idx) => (
                  <tr key={d.id}>
                    <td>{idx + 1}</td>
                    <td>{d.originalFileName}</td>
                    <td>{d.documentCategory || "—"}</td>
                    <td>
                      <span className={`badge ${docAiBadgeClass(d.aiVerificationStatus)}`}>
                        {d.aiVerificationStatus || "PENDING"}
                      </span>
                    </td>
                    <td>{d.aiVerificationNotes || "—"}</td>
                    <td>
                      <button
                        type="button"
                        className="btn secondary"
                        onClick={() => requireReturn(d)}
                        title="Mark this document as requiring operator fix and add template line"
                      >
                        {requiredReturnDocIds.includes(d.id) ? "Return Required" : "Require Return"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ) : (
          <p className="hint">No documents are attached to this application in the system.</p>
        )}

        {app.officerComments?.length ? (
          <>
            <h4>Prior officer comments</h4>
            {app.officerComments.map((c) => <p key={c.id}>- {c.commentText}</p>)}
          </>
        ) : null}

        <div className="workflow-box">
          <p className="workflow-title">Status Workflow Reference (Officer)</p>
          <p className="workflow-line">APPLICATION_RECEIVED {"->"} UNDER_REVIEW</p>
          <p className="workflow-line">UNDER_REVIEW {"->"} PENDING_PRE_SITE_RESUBMISSION | SITE_VISIT_SCHEDULED | PENDING_APPROVAL | REJECTED</p>
          <p className="workflow-line">PRE_SITE_RESUBMITTED {"->"} UNDER_REVIEW | SITE_VISIT_SCHEDULED</p>
          <p className="workflow-line">SITE_VISIT_SCHEDULED/SITE_VISIT_DONE {"->"} AWAITING_POST_SITE_CLARIFICATION | PENDING_APPROVAL</p>
          <p className="workflow-line">AWAITING_POST_SITE_CLARIFICATION {"->"} PENDING_POST_SITE_RESUBMISSION</p>
          <p className="workflow-line">POST_SITE_CLARIFICATION_RESUBMITTED {"->"} AWAITING_POST_SITE_CLARIFICATION | PENDING_APPROVAL</p>
          <p className="workflow-line">PENDING_APPROVAL {"->"} APPROVED | REJECTED</p>
        </div>
        <h4>Feedback</h4>
        {err ? <div className="alert-box error">{err}</div> : null}
        {ok ? <div className="alert-box success">{ok}</div> : null}
        <div className="button-grid">
          <select className="field" value={newStatus} onChange={(e) => setNewStatus(e.target.value)}>
            {allowedTransitions.length ? (
              allowedTransitions.map((s) => <option key={s} value={s}>{s}</option>)
            ) : (
              <option value={currentStatus || app.internalStatus}>{currentStatus || app.internalStatus}</option>
            )}
          </select>
          <textarea className="field" value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Officer comment" />
          <button type="button" className="btn" disabled={submitting || allowedTransitions.length === 0} onClick={submitFeedback}>
            {submitting ? "Submitting..." : "Submit Feedback"}
          </button>
        </div>
      </section>
    </main>
  );
}
