import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../apiClient";
import { TRACK_OPTIONS, requirementsForTrack, requiredCategoriesForTrack } from "../documentRequirements";

export default function OperatorSubmitPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    businessName: "",
    licensingTrack: "ECDC",
    businessType: "",
    businessAddress: "",
    contactPhone: "",
    activityDescription: "",
  });
  const [documents, setDocuments] = useState([]);
  const [dragOver, setDragOver] = useState(false);
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const trackRequirements = requirementsForTrack(form.licensingTrack);
  const requirementByCategory = new Map(trackRequirements.map((r) => [r.category, r]));

  const onChange = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    navigate("/login");
  };

  const toDocumentItem = (file) => ({
    id: `${file.name}-${file.size}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    fileName: file.name,
    contentType: file.type || "application/octet-stream",
    fileSizeBytes: file.size,
    documentCategory: trackRequirements[0]?.category || "GENERAL_SUPPORTING",
    aiVerificationStatus: "PENDING",
    aiVerificationNotes: "Verification queued on submit",
  });

  const addFiles = (fileList) => {
    const nextItems = Array.from(fileList || []).map(toDocumentItem);
    if (!nextItems.length) return;
    setDocuments((prev) => [...prev, ...nextItems]);
  };

  const removeDocument = (id) => setDocuments((prev) => prev.filter((d) => d.id !== id));
  const updateDocumentCategory = (id, category) => {
    setDocuments((prev) => prev.map((d) => (d.id === id ? { ...d, documentCategory: category } : d)));
  };
  useEffect(() => {
    const allowed = new Set(trackRequirements.map((r) => r.category));
    const fallback = trackRequirements[0]?.category || "GENERAL_SUPPORTING";
    setDocuments((prev) => prev.map((d) => (allowed.has(d.documentCategory) ? d : { ...d, documentCategory: fallback })));
  }, [form.licensingTrack]);

  const submit = async (e) => {
    e.preventDefault();
    if (!window.confirm("Submit this application now?")) return;
    setErr("");
    setOk("");
    setSubmitting(true);
    const submittedCategories = new Set(documents.map((d) => d.documentCategory).filter(Boolean));
    const missingRequired = requiredCategoriesForTrack(form.licensingTrack).filter((c) => !submittedCategories.has(c));
    if (missingRequired.length) {
      setErr(`Missing required documents: ${missingRequired.join(", ")}`);
      setSubmitting(false);
      return;
    }

    try {
      const payload = {
        ...form,
        documents: documents.map((d) => ({
          originalFileName: d.fileName,
          contentType: d.contentType,
          fileSizeBytes: d.fileSizeBytes,
          documentCategory: d.documentCategory,
          aiVerificationStatus: d.aiVerificationStatus,
          aiVerificationNotes: d.aiVerificationNotes,
        })),
      };
      const app = await api.submitApplication(payload);
      setOk("Application submitted successfully.");
      navigate(`/operator/applications/${app.id}`);
    } catch (error) {
      setErr(error.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="app-shell">
      <header className="top">
        <h2>New Application</h2>
        <div className="top">
          <button className="btn secondary" onClick={() => navigate("/operator/dashboard")}>Back</button>
          <button className="btn secondary" onClick={logout}>Logout</button>
        </div>
      </header>
      <section className="card">
        <h3>Application Details</h3>
        {err ? <div className="alert-box error">{err}</div> : null}
        {ok ? <div className="alert-box success">{ok}</div> : null}
        <form className="button-grid" onSubmit={submit}>
          <input className="field" placeholder="Business Name" value={form.businessName} onChange={(e) => onChange("businessName", e.target.value)} required />
          <select className="field" value={form.licensingTrack} onChange={(e) => onChange("licensingTrack", e.target.value)} required>
            {TRACK_OPTIONS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
          </select>
          <input className="field" placeholder="Business Type" value={form.businessType} onChange={(e) => onChange("businessType", e.target.value)} required />
          <input className="field" placeholder="Business Address" value={form.businessAddress} onChange={(e) => onChange("businessAddress", e.target.value)} required />
          <input className="field" placeholder="Contact Phone" value={form.contactPhone} onChange={(e) => onChange("contactPhone", e.target.value)} />
          <textarea className="field" placeholder="Activity Description" value={form.activityDescription} onChange={(e) => onChange("activityDescription", e.target.value)} required />
          <div
            className={`dropzone ${dragOver ? "active" : ""}`}
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(false);
              addFiles(e.dataTransfer.files);
            }}
          >
            <p><strong>Document Upload</strong> (Drag and drop files)</p>
            <div className="workflow-box" style={{ marginTop: 10 }}>
              <p className="workflow-title">Required supporting evidence</p>
              <p className="workflow-line">
                Upload existing evidence documents (license/certificates/plans), not a form template.
              </p>
              {trackRequirements.map((r) => (
                <p key={r.category} className="workflow-line">
                  <strong>{r.required ? "[Required]" : "[Optional]"} {r.label}</strong> ({r.category}) — Examples: {r.examples}
                </p>
              ))}
            </div>
            <p className="hint">or use file picker below</p>
            <input
              className="field"
              type="file"
              multiple
              onChange={(e) => addFiles(e.target.files)}
            />
          </div>
          {documents.length ? (
            <table>
              <thead>
                <tr>
                  <th>File</th>
                  <th>Size (KB)</th>
                  <th>Category</th>
                  <th>AI Verification</th>
                  <th>Notes</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {documents.map((d) => (
                  <tr key={d.id}>
                    <td>{d.fileName}</td>
                    <td>{(d.fileSizeBytes / 1024).toFixed(1)}</td>
                    <td>
                      <select
                        className="field"
                        value={d.documentCategory}
                        onChange={(e) => updateDocumentCategory(d.id, e.target.value)}
                      >
                        {trackRequirements.map((r) => (
                          <option key={r.category} value={r.category}>
                            {r.label} ({r.category})
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <span className={`badge ${d.aiVerificationStatus === "PASSED" ? "badge-green" : d.aiVerificationStatus === "FLAGGED" ? "badge-red" : "badge-amber"}`}>
                        {d.aiVerificationStatus}
                      </span>
                    </td>
                    <td>{d.aiVerificationNotes}<br /><span className="hint">Rule: {requirementByCategory.get(d.documentCategory)?.aiRule || "General rule"}</span></td>
                    <td>
                      <button type="button" className="btn secondary" onClick={() => removeDocument(d.id)}>
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
          <button className="btn" type="submit" disabled={submitting}>
            {submitting ? "Submitting..." : "Submit Application"}
          </button>
        </form>
      </section>
    </main>
  );
}
