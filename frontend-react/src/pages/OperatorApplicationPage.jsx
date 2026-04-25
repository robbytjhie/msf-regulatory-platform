import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../apiClient";
import { statusClass } from "../statusUtils";

function formatTs(value) {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString();
}

export default function OperatorApplicationPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [app, setApp] = useState(null);
  const [flagged, setFlagged] = useState([]);
  const [resubmit, setResubmit] = useState({ businessName: "", businessAddress: "", contactPhone: "", activityDescription: "" });
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [respondingId, setRespondingId] = useState(null);
  const [docPollActive, setDocPollActive] = useState(false);
  const [lastDocPollAt, setLastDocPollAt] = useState(null);
  const [nowTick, setNowTick] = useState(() => Date.now());

  useEffect(() => {
    api.getOperatorApplication(id).then(setApp).catch((e) => setErr(e.message));
    api.getFlaggedItems(id).then(setFlagged).catch(() => {});
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
        const docs = await api.getOperatorDocumentStatuses(id);
        setLastDocPollAt(Date.now());
        setApp((prev) => (prev ? { ...prev, documents: docs } : prev));
        if (!docs.some((d) => d.aiVerificationStatus === "PENDING" || d.aiVerificationStatus === "PROCESSING")) {
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
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    navigate("/login");
  };

  const submitResubmit = async () => {
    if (!canResubmit) return;
    if (!window.confirm("Submit resubmission now?")) return;
    try {
      setSubmitting(true);
      setErr("");
      setOk("");
      const updated = await api.resubmitApplication(id, resubmit);
      setApp(updated);
      setOk("Resubmission submitted successfully.");
    } catch (e) {
      setErr(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  const respond = async (itemId) => {
    const message = window.prompt("Enter response");
    if (!message) return;
    if (!window.confirm("Submit this clarification response?")) return;
    try {
      setRespondingId(itemId);
      await api.respondToItem(itemId, message);
      const items = await api.getFlaggedItems(id);
      setFlagged(items);
      setOk("Clarification response submitted.");
      setErr("");
    } catch (e) {
      setErr(e.message);
    } finally {
      setRespondingId(null);
    }
  };

  if (!app) return <main className="app-shell">{err ? <p className="error">{err}</p> : <p>Loading...</p>}</main>;

  const canResubmit = app.statusLabel === "Pending Pre-Site Resubmission" || app.statusLabel === "Awaiting Post-Site Resubmission";

  return (
    <main className="app-shell">
      <header className="top">
        <h2>{app.businessName}</h2>
        <div className="top">
          <button className="btn secondary" onClick={() => navigate("/operator/dashboard")}>Back</button>
          <button className="btn secondary" onClick={logout}>Logout</button>
        </div>
      </header>
      <section className="card">
        <h3>{app.referenceNumber} <span className={`badge ${statusClass(app.statusLabel)}`}>{app.statusLabel}</span></h3>
        <p>Track: {app.licensingTrack || "-"}</p>
        <p>Submission Round #{app.submissionRound}</p>
        <p><strong>Final Outcome:</strong> {app.statusLabel === "Approved" || app.statusLabel === "Rejected" ? app.statusLabel : "In Progress"}</p>
        {err ? <div className="alert-box error">{err}</div> : null}
        {ok ? <div className="alert-box success">{ok}</div> : null}
        {app.statusHistory?.length ? (
          <>
            <h4>Shared Timeline</h4>
            <table>
              <thead><tr><th>When</th><th>From</th><th>To</th></tr></thead>
              <tbody>
                {app.statusHistory.map((h, idx) => (
                  <tr key={`${h.changedAt || idx}-${h.toStatusLabel || idx}`}>
                    <td>{formatTs(h.changedAt)}</td>
                    <td>{h.fromStatusLabel || "Initial submission"}</td>
                    <td>{h.toStatusLabel || "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ) : null}
        {app.officerComments?.length ? (
          <>
            <h4>Officer Feedback</h4>
            {app.officerComments.map((c) => <p key={c.id}>- {c.commentText}</p>)}
          </>
        ) : null}
        {app.documents?.length ? (
          <>
            <div className="doc-table-header">
              <h4>Uploaded Documents</h4>
              {docPollActive ? (
                <p className="doc-poll-meta">
                  <span className="live">Live updates</span>
                  {lastDocPollAt != null
                    ? ` · Last updated ${Math.max(0, Math.floor((nowTick - lastDocPollAt) / 1000))}s ago`
                    : " · syncing…"}
                </p>
              ) : null}
            </div>
            <table>
              <thead><tr><th>File</th><th>Category</th><th>AI Verification</th><th>Notes</th></tr></thead>
              <tbody>
                {app.documents.map((d) => (
                  <tr key={d.id}>
                    <td>{d.originalFileName}</td>
                    <td>{d.documentCategory || "-"}</td>
                    <td>
                      <span className={`badge ${d.aiVerificationStatus === "PASSED" ? "badge-green" : d.aiVerificationStatus === "FLAGGED" ? "badge-red" : "badge-amber"}`}>
                        {d.aiVerificationStatus || "PENDING"}
                      </span>
                    </td>
                    <td>{d.aiVerificationNotes || "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ) : null}
        <h4>Resubmit Updated Fields</h4>
        {!canResubmit ? <div className="hint">Resubmission is only available when status is Pending Pre-Site Resubmission or Awaiting Post-Site Resubmission.</div> : null}
        <div className="button-grid">
          <input className="field" placeholder="Business Name (optional)" onChange={(e) => setResubmit((r) => ({ ...r, businessName: e.target.value }))} />
          <input className="field" placeholder="Business Address (optional)" onChange={(e) => setResubmit((r) => ({ ...r, businessAddress: e.target.value }))} />
          <input className="field" placeholder="Contact Phone (optional)" onChange={(e) => setResubmit((r) => ({ ...r, contactPhone: e.target.value }))} />
          <textarea className="field" placeholder="Activity Description (optional)" onChange={(e) => setResubmit((r) => ({ ...r, activityDescription: e.target.value }))} />
          <button className="btn" disabled={!canResubmit || submitting} onClick={submitResubmit}>
            {submitting ? "Submitting..." : "Submit Resubmission"}
          </button>
        </div>
        {flagged.length ? (
          <>
            <h4>Flagged Checklist Items</h4>
            <table>
              <thead><tr><th>Code</th><th>Title</th><th>Comment</th><th>Action</th></tr></thead>
              <tbody>
                {flagged.map((f) => (
                  <tr key={f.id}>
                    <td>{f.itemCode}</td>
                    <td>{f.itemTitle}</td>
                    <td>{f.officerComment}</td>
                    <td>
                      <button className="btn secondary" disabled={respondingId === f.id} onClick={() => respond(f.id)}>
                        {respondingId === f.id ? "Submitting..." : "Respond"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ) : null}
      </section>
    </main>
  );
}
