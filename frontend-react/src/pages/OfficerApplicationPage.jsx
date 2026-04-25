import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../apiClient";
import { statusClass } from "../statusUtils";

const transitionMap = {
  APPLICATION_RECEIVED: ["UNDER_REVIEW"],
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
  const [selectedIssueDocIds, setSelectedIssueDocIds] = useState([]);

  useEffect(() => {
    api.getOfficerApplication(id).then((d) => {
      setApp(d);
      const defaultIssueDocIds = (d.documents || [])
        .filter((doc) => doc.aiVerificationStatus === "FLAGGED" || doc.aiVerificationStatus === "FAILED")
        .map((doc) => doc.id);
      setSelectedIssueDocIds(defaultIssueDocIds);
      const allowed = transitionMap[d.internalStatus] || [];
      setNewStatus(allowed[0] || d.internalStatus || "UNDER_REVIEW");
    }).catch((e) => setErr(e.message));
  }, [id]);

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
      const documentFixComments = selectedIssueDocIds.map((docId) => {
        const doc = app.documents?.find((d) => d.id === docId);
        const reason = doc?.aiVerificationNotes || "Document requires correction based on officer review.";
        return {
          commentText: `[Document Fix Required] ${doc?.originalFileName || "Document"} (${doc?.documentCategory || "-"}) - ${reason}`,
          targetSection: "document",
          targetDocumentId: docId,
        };
      });
      const commentsPayload = [
        ...(comment ? [{ commentText: comment, targetSection: null, targetDocumentId: null }] : []),
        ...documentFixComments,
      ];
      const updated = await api.submitOfficerFeedback(id, {
        newStatus,
        statusNotes: "Updated from React dashboard",
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

  const addAiReasonToComment = (doc) => {
    const reason = doc?.aiVerificationNotes || "AI reason not available.";
    const line = `[AI reason] ${doc?.originalFileName || "Document"} (${doc?.documentCategory || "-"}) - ${reason}`;
    setComment((prev) => (prev ? `${prev}\n${line}` : line));
    setOk("AI reason inserted into officer comment. You can edit before submit.");
  };
  const toggleIssueDoc = (docId) => {
    setSelectedIssueDocIds((prev) => (prev.includes(docId) ? prev.filter((id) => id !== docId) : [...prev, docId]));
  };

  if (!app) return <main className="app-shell">{err ? <p className="error">{err}</p> : <p>Loading...</p>}</main>;
  const allowedTransitions = transitionMap[app.internalStatus] || [];
  const checklistEnabled = CHECKLIST_AVAILABLE_STATUSES.has(app.internalStatus);

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
        <p><strong>Current internal state:</strong> {app.internalStatus}</p>
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
              <thead><tr><th>Require Fix</th><th>File</th><th>Category</th><th>AI status</th><th>Notes (why flagged, etc.)</th><th>Action</th></tr></thead>
              <tbody>
                {app.documents.map((d) => (
                  <tr key={d.id}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selectedIssueDocIds.includes(d.id)}
                        onChange={() => toggleIssueDoc(d.id)}
                        title="Mark this document as still requiring operator fix"
                      />
                    </td>
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
                        onClick={() => addAiReasonToComment(d)}
                        disabled={!d.aiVerificationNotes}
                        title="Insert this AI reason into the feedback comment"
                      >
                        Use AI reason
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
              <option value={app.internalStatus}>{app.internalStatus}</option>
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
