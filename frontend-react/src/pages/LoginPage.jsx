import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../apiClient";

const demoAccounts = [
  { label: "Officer", email: "officer@gov.sg", password: "password" },
  { label: "Operator (ACME)", email: "operator@acme.sg", password: "password" },
  { label: "Operator (Beta)", email: "operator2@beta.sg", password: "password" },
];

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const data = await api.login(email, password);
      // Use sessionStorage to isolate logins per browser tab/window.
      sessionStorage.setItem("token", data.token);
      sessionStorage.setItem("user", JSON.stringify(data));
      // Clear legacy/shared storage to avoid cross-role leakage.
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      navigate(data.role === "OFFICER" ? "/officer/dashboard" : "/operator/dashboard");
    } catch (err) {
      setError(err.message || "Sign in failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-shell">
      <div className="login-card">
        <h1>Regulatory &amp; Licensing Platform</h1>
        <p className="sub">Sign in with your account.</p>
        {error ? <p className="error">{error}</p> : null}
        <form className="button-grid" onSubmit={onSubmit}>
          <input className="field" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" required />
          <input className="field" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password" required />
          <button className="btn" disabled={loading} type="submit">{loading ? "Signing in..." : "Sign in"}</button>
        </form>
        <div className="demo-accounts">
          <p className="demo-title">Demo accounts</p>
          {demoAccounts.map((a) => (
            <div className="demo-row" key={a.email}>
              <span>{a.label}</span>
              <button type="button" className="demo-fill" onClick={() => { setEmail(a.email); setPassword(a.password); }}>Fill</button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
