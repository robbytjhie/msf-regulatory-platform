import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../apiClient";

export default function OperatorSubmitPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    businessName: "",
    businessType: "",
    businessAddress: "",
    contactPhone: "",
    activityDescription: "",
  });
  const [err, setErr] = useState("");
  const [ok, setOk] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const onChange = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    navigate("/login");
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!window.confirm("Submit this application now?")) return;
    setErr("");
    setOk("");
    setSubmitting(true);
    try {
      const app = await api.submitApplication(form);
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
          <input className="field" placeholder="Business Type" value={form.businessType} onChange={(e) => onChange("businessType", e.target.value)} required />
          <input className="field" placeholder="Business Address" value={form.businessAddress} onChange={(e) => onChange("businessAddress", e.target.value)} required />
          <input className="field" placeholder="Contact Phone" value={form.contactPhone} onChange={(e) => onChange("contactPhone", e.target.value)} />
          <textarea className="field" placeholder="Activity Description" value={form.activityDescription} onChange={(e) => onChange("activityDescription", e.target.value)} required />
          <button className="btn" type="submit" disabled={submitting}>
            {submitting ? "Submitting..." : "Submit Application"}
          </button>
        </form>
      </section>
    </main>
  );
}
