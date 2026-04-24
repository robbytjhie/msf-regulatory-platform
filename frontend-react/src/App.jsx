import { useEffect, useState } from "react";
import "./App.css";

const demoAccounts = [
  { label: "Officer", email: "officer@gov.sg", password: "password" },
  { label: "Operator (ACME)", email: "operator@acme.sg", password: "password" },
  { label: "Operator (Beta)", email: "operator2@beta.sg", password: "password" },
];

async function apiRequest(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  const json = await response.json();
  if (!response.ok) {
    throw new Error(json.message || `HTTP ${response.status}`);
  }
  return json;
}

function App() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [session, setSession] = useState(() => {
    const saved = localStorage.getItem("session");
    return saved ? JSON.parse(saved) : null;
  });
  const [applications, setApplications] = useState([]);
  const [status, setStatus] = useState("idle");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!session) return;
    const load = async () => {
      setStatus("loading");
      setError("");
      try {
        const endpoint =
          session.role === "OFFICER"
            ? "/api/officer/applications"
            : "/api/operator/applications";
        const result = await apiRequest(endpoint, {
          headers: { Authorization: `Bearer ${session.token}` },
        });
        setApplications(result.data || []);
        setStatus("ready");
      } catch (err) {
        setStatus("error");
        setError(err.message);
      }
    };
    load();
  }, [session]);

  const login = async (event) => {
    event.preventDefault();
    setStatus("loading");
    setError("");
    try {
      const result = await apiRequest("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      localStorage.setItem("session", JSON.stringify(result.data));
      setSession(result.data);
      setStatus("ready");
    } catch (err) {
      setStatus("error");
      setError(err.message);
    }
  };

  const logout = () => {
    localStorage.removeItem("session");
    setSession(null);
    setApplications([]);
    setStatus("idle");
    setError("");
  };

  const fillDemo = (account) => {
    setEmail(account.email);
    setPassword(account.password);
    setError("");
  };

  if (!session) {
    return (
      <div className="login-shell">
        <div className="login-card">
          <h1>Regulatory &amp; Licensing Platform</h1>
          <p className="sub">Sign in with your account.</p>
          {error ? <p className="error">{error}</p> : null}
          <form className="button-grid" onSubmit={login}>
            <input
              className="field"
              type="email"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <input
              className="field"
              type="password"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <button className="btn" disabled={status === "loading"} type="submit">
              {status === "loading" ? "Signing in..." : "Sign in"}
            </button>
          </form>
          <div className="demo-accounts">
            <p className="demo-title">Demo accounts</p>
            {demoAccounts.map((account) => (
              <div key={account.email} className="demo-row">
                <span>{account.label}</span>
                <button
                  type="button"
                  className="demo-fill"
                  onClick={() => fillDemo(account)}
                >
                  Fill
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <main className="app-shell">
      <header className="top">
        <div>
          <h2>Welcome, {session.fullName}</h2>
          <p>{session.role}</p>
        </div>
        <button className="btn secondary" onClick={logout}>
          Logout
        </button>
      </header>

      {status === "loading" ? <p>Loading applications...</p> : null}
      {error ? <p className="error">{error}</p> : null}

      <section className="card">
        <h3>Applications</h3>
        {applications.length === 0 && status === "ready" ? (
          <p>No applications found.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Reference</th>
                <th>Business</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {applications.map((item) => (
                <tr key={item.id}>
                  <td>{item.referenceNumber}</td>
                  <td>{item.businessName}</td>
                  <td>{item.displayStatus || item.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </main>
  );
}

export default App;
