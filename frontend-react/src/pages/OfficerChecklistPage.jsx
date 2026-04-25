import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../apiClient";

export default function OfficerChecklistPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [savingDraft, setSavingDraft] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [applicationInternalStatus, setApplicationInternalStatus] = useState("");

  useEffect(() => {
    Promise.all([
      api.getChecklist(id),
      api.getOfficerApplication(id),
    ])
      .then(([checklist, application]) => {
        setItems(checklist);
        setApplicationInternalStatus(application?.internalStatus || "");
      })
      .catch((e) => setErr(e.message));
  }, [id]);

  const logout = () => {
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("user");
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    navigate("/login");
  };

  const updateItem = (itemId, patch) => {
    setItems((prev) => prev.map((i) => (i.id === itemId ? { ...i, ...patch } : i)));
  };

  const payload = () => ({
    items: items.map((i) => ({
      itemId: i.id,
      status: i.status,
      officerComment: i.officerComment || null,
    })),
  });
  const hasPendingItems = items.some((i) => i.status === "PENDING");
  const allChecklistResolved = items.length > 0 && items.every((i) => i.status === "SATISFACTORY" || i.status === "RESOLVED");
  const inApprovalOrTerminal =
    applicationInternalStatus === "PENDING_APPROVAL"
    || applicationInternalStatus === "APPROVED"
    || applicationInternalStatus === "REJECTED";
  const checklistLocked = allChecklistResolved && inApprovalOrTerminal;

  const saveDraft = async () => {
    if (!window.confirm("Save checklist draft?")) return;
    try {
      setSavingDraft(true);
      await api.saveChecklistDraft(id, payload());
      setOk("Draft saved.");
      setErr("");
    } catch (e) {
      setErr(e.message);
    } finally {
      setSavingDraft(false);
    }
  };

  const submitChecklist = async () => {
    if (!window.confirm("Submit checklist now? This may transition application state.")) return;
    try {
      setSubmitting(true);
      await api.submitChecklist(id, payload());
      navigate(`/officer/applications/${id}`);
    } catch (e) {
      setErr(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="app-shell">
      <header className="top">
        <h2>Site Checklist</h2>
        <div className="top">
          <button className="btn secondary" onClick={() => navigate(`/officer/applications/${id}`)}>Back</button>
          <button className="btn secondary" onClick={logout}>Logout</button>
        </div>
      </header>
      <section className="card">
        {err ? <div className="alert-box error">{err}</div> : null}
        {ok ? <div className="alert-box success">{ok}</div> : null}
        <table>
          <thead><tr><th>Code</th><th>Title</th><th>Status</th><th>Comment</th></tr></thead>
          <tbody>
            {items.map((i) => (
              <tr key={i.id}>
                <td>{i.itemCode}</td>
                <td>{i.itemTitle}</td>
                <td>
                  <select
                    className="field"
                    value={i.status}
                    disabled={checklistLocked}
                    onChange={(e) => updateItem(i.id, { status: e.target.value })}
                  >
                    <option value="PENDING">PENDING</option>
                    <option value="SATISFACTORY">SATISFACTORY</option>
                    <option value="NEEDS_CLARIFICATION">NEEDS_CLARIFICATION</option>
                    <option value="RESOLVED">RESOLVED</option>
                  </select>
                </td>
                <td>
                  <input
                    className="field"
                    disabled={checklistLocked}
                    value={i.officerComment || ""}
                    onChange={(e) => updateItem(i.id, { officerComment: e.target.value })}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {checklistLocked ? (
          <p className="hint" style={{ marginTop: 10 }}>
            Checklist is locked because all items are resolved and application is already in approval/terminal stage.
          </p>
        ) : null}
        {hasPendingItems ? (
          <p className="hint" style={{ marginTop: 10 }}>
            Submission is disabled while checklist items are still <code>PENDING</code>.
          </p>
        ) : null}
        <div className="top" style={{ marginTop: 12 }}>
          <button className="btn secondary" disabled={savingDraft || checklistLocked} onClick={saveDraft}>
            {savingDraft ? "Saving..." : "Save Draft"}
          </button>
          <button className="btn" disabled={submitting || hasPendingItems || checklistLocked} onClick={submitChecklist}>
            {submitting ? "Submitting..." : "Submit Checklist"}
          </button>
        </div>
      </section>
    </main>
  );
}
