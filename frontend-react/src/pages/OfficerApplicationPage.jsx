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

export default function OfficerApplicationPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [app, setApp] = useState(null);
  const [newStatus, setNewStatus] = useState("UNDER_REVIEW");
  const [comment, setComment] = useState("");
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    api.getOfficerApplication(id).then((d) => {
      setApp(d);
      const allowed = transitionMap[d.internalStatus] || [];
      setNewStatus(allowed[0] || d.internalStatus || "UNDER_REVIEW");
    }).catch((e) => setErr(e.message));
  }, [id]);

  const logout = () => {
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
      const updated = await api.submitOfficerFeedback(id, {
        newStatus,
        statusNotes: "Updated from React dashboard",
        comments: comment ? [{ commentText: comment, targetSection: null }] : [],
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

  if (!app) return <main className="app-shell">{err ? <p className="error">{err}</p> : <p>Loading...</p>}</main>;
  const allowedTransitions = transitionMap[app.internalStatus] || [];

  return (
    <main className="app-shell">
      <header className="top">
        <h2>{app.businessName}</h2>
        <div className="top">
          <button className="btn secondary" onClick={() => navigate("/officer/dashboard")}>Back</button>
          <button className="btn secondary" onClick={() => navigate(`/officer/applications/${id}/checklist`)}>Checklist</button>
          <button className="btn secondary" onClick={logout}>Logout</button>
        </div>
      </header>
      <section className="card">
        <h3>{app.referenceNumber} <span className={`badge ${statusClass(app.statusLabel)}`}>{app.statusLabel}</span></h3>
        <p>Operator: {app.operatorName} ({app.operatorEmail})</p>
        <p>Submission Round #{app.submissionRound}</p>
        <p><strong>Final Outcome:</strong> {app.statusLabel === "Approved" || app.statusLabel === "Rejected" ? app.statusLabel : "In Progress"}</p>
        <p><strong>Current internal state:</strong> {app.internalStatus}</p>
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
          <button className="btn" disabled={submitting || allowedTransitions.length === 0} onClick={submitFeedback}>
            {submitting ? "Submitting..." : "Submit Feedback"}
          </button>
        </div>
      </section>
    </main>
  );
}
