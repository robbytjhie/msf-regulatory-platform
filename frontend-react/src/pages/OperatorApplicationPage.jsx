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
  const [replacementFiles, setReplacementFiles] = useState({});
  const [validatingDocId, setValidatingDocId] = useState(null);

  useEffect(() => {
    api.getOperatorApplication(id).then((data) => {
      setApp(data);
      setResubmit({
        businessName: data.businessName || "",
        businessAddress: data.businessAddress || "",
        contactPhone: data.contactPhone || "",
        activityDescription: data.activityDescription || "",
      });
    }).catch((e) => setErr(e.message));
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
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("user");
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

  const reuploadAndValidate = async (doc) => {
    const file = replacementFiles[doc.id];
    if (!file) {
      setErr("Please choose a replacement file before validation.");
      return;
    }
    try {
      setValidatingDocId(doc.id);
      setErr("");
      setOk("");
      await api.reuploadOperatorDocument(id, doc.id, {
        originalFileName: file.name,
        contentType: file.type || "application/octet-stream",
        fileSizeBytes: file.size,
      });
      const fresh = await api.getOperatorApplication(id);
      setApp(fresh);
      setOk(`Re-uploaded ${file.name}. AI validation started automatically.`);
      setDocPollActive(true);
    } catch (e) {
      setErr(e.message);
    } finally {
      setValidatingDocId(null);
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
  const unresolvedIssueDocIds = new Set(
    (app.officerComments || [])
      .filter((c) => c.targetDocumentId && c.resolved === false)
      .map((c) => c.targetDocumentId),
  );
  const issueDocuments = (app.documents || []).filter(
    (d) => unresolvedIssueDocIds.has(d.id) || d.aiVerificationStatus === "FLAGGED" || d.aiVerificationStatus === "FAILED",
  );
  const allIssueDocsValidated = issueDocuments.every((d) => d.aiVerificationStatus === "PASSED");
  const canSubmitResubmission = canResubmit && allIssueDocsValidated;
  const validatedIssueCount = issueDocuments.filter((d) => d.aiVerificationStatus === "PASSED").length;

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
              <h4>All Uploaded Documents</h4>
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
        <h4>Documents Requiring Fix</h4>
        {issueDocuments.length ? (
          <p className="hint">
            Validation progress: {validatedIssueCount}/{issueDocuments.length} issue document(s) passed.
          </p>
        ) : null}
        {issueDocuments.length ? (
          <table>
            <thead><tr><th>File</th><th>Category</th><th>AI Verification</th><th>Notes</th><th>Replace File</th><th>Validate</th></tr></thead>
            <tbody>
              {issueDocuments.map((d) => (
                <tr key={`issue-${d.id}`}>
                  <td>{d.originalFileName}</td>
                  <td>{d.documentCategory || "-"}</td>
                  <td>
                    <span className={`badge ${d.aiVerificationStatus === "PASSED" ? "badge-green" : d.aiVerificationStatus === "FLAGGED" ? "badge-red" : "badge-amber"}`}>
                      {d.aiVerificationStatus || "PENDING"}
                    </span>
                  </td>
                  <td>{d.aiVerificationNotes || "-"}</td>
                  <td>
                    <input
                      className="field"
                      style={{ maxWidth: 190, padding: "6px 8px", fontSize: 12 }}
                      type="file"
                      onChange={(e) => setReplacementFiles((prev) => ({ ...prev, [d.id]: e.target.files?.[0] || null }))}
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      className="btn secondary"
                      style={{ whiteSpace: "nowrap", padding: "8px 10px" }}
                      disabled={validatingDocId === d.id}
                      onClick={() => reuploadAndValidate(d)}
                    >
                      {validatingDocId === d.id ? "Validating..." : "Re-upload & Validate"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : <p className="hint">No unresolved document issues at this time.</p>}
        <h4>Resubmit Updated Fields</h4>
        {!canResubmit ? (
          <div className="hint">Resubmission is available only after officer returns the case for correction.</div>
        ) : (
          <>
            <div className="button-grid">
              <input className="field" value={resubmit.businessName} placeholder="Business Name (optional)" onChange={(e) => setResubmit((r) => ({ ...r, businessName: e.target.value }))} />
              <input className="field" value={resubmit.businessAddress} placeholder="Business Address (optional)" onChange={(e) => setResubmit((r) => ({ ...r, businessAddress: e.target.value }))} />
              <input className="field" value={resubmit.contactPhone} placeholder="Contact Phone (optional)" onChange={(e) => setResubmit((r) => ({ ...r, contactPhone: e.target.value }))} />
              <textarea className="field" value={resubmit.activityDescription} placeholder="Activity Description (optional)" onChange={(e) => setResubmit((r) => ({ ...r, activityDescription: e.target.value }))} />
              <button className="btn" disabled={!canSubmitResubmission || submitting} onClick={submitResubmit}>
                {submitting ? "Submitting..." : "Submit Resubmission"}
              </button>
            </div>
            {!allIssueDocsValidated && issueDocuments.length ? (
              <p className="hint">
                Submit is disabled until all issue documents are re-uploaded and validated as PASSED.
              </p>
            ) : null}
          </>
        )}
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
