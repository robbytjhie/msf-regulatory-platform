import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../apiClient";
import { TRACK_OPTIONS, requirementsForTrack, requiredCategoriesForTrack } from "../documentRequirements";

const BUSINESS_TYPES_BY_TRACK = {
  ECDC: [
    "Early Childhood Development Centre (ECDC)",
    "Preschool (Infant/Nursery/Kindergarten)",
    "Child Care Centre",
  ],
  SCFA: [
    "Student Care Centre (SCFA)",
    "Student Care + Enrichment Centre",
  ],
  HFAA: [
    "Home for the Aged (HFAA)",
    "Sheltered Home for Elderly",
  ],
  CHILDMINDING: [
    "Childminding Pilot Service",
    "Home-based Infant Care Service",
  ],
};

const EXPECTED_FORMATS_BY_CATEGORY = {
  REGISTRATION_DOC: "PDF, DOC, DOCX, TXT",
  FLOOR_PLAN: "PDF, PNG, JPG, SVG",
  CCTV_AND_SAFETY_PROOF: "PDF, PNG, JPG, DOC, DOCX",
  ATTENDANCE_LOG: "CSV, XLS, XLSX, PDF",
  SUBSIDY_WITHDRAWAL_FORM: "PDF, DOC, DOCX, XLS, XLSX",
  ENVIRONMENT_COMPLIANCE_RECORD: "PDF, XLS, XLSX, CSV",
  STAFF_ROSTER: "PDF, XLS, XLSX, CSV",
  SANITATION_AND_FIRE_CERT: "PDF, PNG, JPG",
  STAFF_MEDICAL_SCREENING: "PDF, PNG, JPG",
  HOME_SAFETY_PHOTOS: "PNG, JPG, JPEG, SVG",
  EQUIPMENT_INVENTORY: "PDF, XLS, XLSX, CSV",
  CAPACITY_PLAN: "PDF, DOC, DOCX, XLS, XLSX",
  GENERAL_SUPPORTING: "PDF, DOC, DOCX, PNG, JPG, CSV",
};

export default function OperatorSubmitPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    businessName: "",
    licensingTrack: "",
    businessType: "",
    businessAddress: "",
    contactPhone: "",
    activityDescription: "",
  });
  const [documents, setDocuments] = useState([]);
  const [dragOverCategory, setDragOverCategory] = useState(null);
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const trackRequirements = useMemo(() => requirementsForTrack(form.licensingTrack), [form.licensingTrack]);
  const businessTypeOptions = useMemo(
    () => (form.licensingTrack ? (BUSINESS_TYPES_BY_TRACK[form.licensingTrack] || []) : []),
    [form.licensingTrack],
  );
  const orderedRequirements = useMemo(
    () => [...trackRequirements].sort((a, b) => Number(b.required) - Number(a.required)),
    [trackRequirements],
  );
  const requirementByCategory = useMemo(
    () => new Map(trackRequirements.map((r) => [r.category, r])),
    [trackRequirements],
  );

  const onChange = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const logout = () => {
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("user");
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    navigate("/login");
  };

  const toDocumentItem = (file, category) => ({
    id: `${file.name}-${file.size}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    fileName: file.name,
    contentType: file.type || "application/octet-stream",
    fileSizeBytes: file.size,
    documentCategory: category,
    aiVerificationStatus: "PENDING",
    aiVerificationNotes: "Validation queued",
  });

  const addFiles = (category, fileList) => {
    const nextItems = Array.from(fileList || []).map((file) => toDocumentItem(file, category));
    if (!nextItems.length) return;
    setDocuments((prev) => [...prev, ...nextItems]);
  };

  const removeDocument = (id) => setDocuments((prev) => prev.filter((d) => d.id !== id));
  const documentsByCategory = useMemo(() => {
    const map = new Map(trackRequirements.map((r) => [r.category, []]));
    for (const d of documents) {
      if (map.has(d.documentCategory)) map.get(d.documentCategory).push(d);
    }
    return map;
  }, [documents, trackRequirements]);

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

  const uploadReady = Boolean(form.licensingTrack && form.businessType);

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
          <label className="hint" htmlFor="licensing-track">Licensing track (required)</label>
          <select
            id="licensing-track"
            className="field"
            value={form.licensingTrack}
            onChange={(e) => {
              onChange("licensingTrack", e.target.value);
              onChange("businessType", "");
              setDocuments([]);
            }}
            required
          >
            <option value="">Select Licensing Track</option>
            {TRACK_OPTIONS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
          </select>
          <label className="hint" htmlFor="business-type">Business type (required)</label>
          <select
            id="business-type"
            className="field"
            value={form.businessType}
            onChange={(e) => onChange("businessType", e.target.value)}
            disabled={!form.licensingTrack}
            required
          >
            <option value="">
              {form.licensingTrack ? "Select Business Type (MSF)" : "Select Licensing Track first"}
            </option>
            {businessTypeOptions.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
          <input className="field" placeholder="Business Address" value={form.businessAddress} onChange={(e) => onChange("businessAddress", e.target.value)} required />
          <input className="field" placeholder="Contact Phone" value={form.contactPhone} onChange={(e) => onChange("contactPhone", e.target.value)} />
          <textarea className="field" placeholder="Activity Description" value={form.activityDescription} onChange={(e) => onChange("activityDescription", e.target.value)} required />
          {uploadReady ? (
            <>
              <div className="workflow-box" style={{ marginTop: 10 }}>
                <p className="workflow-title">Supporting Document Upload</p>
                <p className="workflow-line">Mandatory items are listed first. Upload files into the correct section below.</p>
              </div>
              {orderedRequirements.map((r) => (
                <div
                  key={r.category}
                  className={`dropzone ${dragOverCategory === r.category ? "active" : ""}`}
                  onDragOver={(e) => {
                    e.preventDefault();
                    setDragOverCategory(r.category);
                  }}
                  onDragLeave={() => setDragOverCategory(null)}
                  onDrop={(e) => {
                    e.preventDefault();
                    setDragOverCategory(null);
                    addFiles(r.category, e.dataTransfer.files);
                  }}
                >
                  <p>
                    <strong>{r.required ? "[Required]" : "[Optional]"} {r.label}</strong> ({r.category})
                  </p>
                  <p className="hint">Examples: {r.examples}</p>
                  <p className="hint">AI rule: {requirementByCategory.get(r.category)?.aiRule}</p>
                  <p className="hint">Expected file format: {EXPECTED_FORMATS_BY_CATEGORY[r.category] || "PDF, DOC, DOCX, PNG, JPG, CSV"}</p>
                  <input className="field" type="file" multiple onChange={(e) => addFiles(r.category, e.target.files)} />
                  {documentsByCategory.get(r.category)?.length ? (
                    <table style={{ marginTop: 10 }}>
                      <thead><tr><th>File</th><th>Size (KB)</th><th>Action</th></tr></thead>
                      <tbody>
                        {documentsByCategory.get(r.category).map((d) => (
                          <tr key={d.id}>
                            <td>{d.fileName}</td>
                            <td>{(d.fileSizeBytes / 1024).toFixed(1)}</td>
                            <td>
                              <button type="button" className="btn secondary" onClick={() => removeDocument(d.id)}>Remove</button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  ) : null}
                </div>
              ))}
              <div className="top" style={{ justifyContent: "flex-start", gap: 10 }}>
                <button className="btn" type="submit" disabled={submitting}>
                  {submitting ? "Submitting..." : "Submit Application"}
                </button>
              </div>
            </>
          ) : (
            <p className="hint">Select both Licensing track and Business type to start supporting document upload.</p>
          )}
        </form>
      </section>
    </main>
  );
}
