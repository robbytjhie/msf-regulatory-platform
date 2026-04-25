import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../apiClient";
import { IN_PROGRESS, NEEDS_ACTION, TERMINAL, pendingOwnerByInternalStatus, statusClass } from "../statusUtils";

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export default function OfficerDashboardPage() {
  const navigate = useNavigate();
  const [apps, setApps] = useState([]);
  const [filter, setFilter] = useState("all");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [sortBy, setSortBy] = useState("lastModifiedDesc");
  const [gotoPage, setGotoPage] = useState("");

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const data = await api.listOfficerApps();
        if (active) setApps(data);
      } catch {
        // Keep current dashboard state when a polling call fails.
      }
    };
    load();
    const timer = window.setInterval(load, 2000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  const filtered = useMemo(() => {
    let list = apps;
    if (filter === "needs-action") list = list.filter((a) => NEEDS_ACTION.has(a.internalStatus));
    if (filter === "in-progress") list = list.filter((a) => IN_PROGRESS.has(a.internalStatus));
    if (filter === "terminal") list = list.filter((a) => TERMINAL.has(a.internalStatus));
    if (query.trim()) {
      const q = query.toLowerCase();
      list = list.filter((a) => a.businessName.toLowerCase().includes(q) || a.referenceNumber.toLowerCase().includes(q));
    }
    const sorted = [...list];
    if (sortBy === "lastModifiedDesc") sorted.sort((a, b) => new Date(b.lastModifiedAt) - new Date(a.lastModifiedAt));
    if (sortBy === "lastModifiedAsc") sorted.sort((a, b) => new Date(a.lastModifiedAt) - new Date(b.lastModifiedAt));
    if (sortBy === "businessAsc") sorted.sort((a, b) => a.businessName.localeCompare(b.businessName));
    if (sortBy === "businessDesc") sorted.sort((a, b) => b.businessName.localeCompare(a.businessName));
    return sorted;
  }, [apps, filter, query, sortBy]);

  useEffect(() => {
    setPage(1);
  }, [filter, query, pageSize, sortBy]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const paged = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const start = filtered.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const end = Math.min(currentPage * pageSize, filtered.length);

  const logout = () => {
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("user");
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    navigate("/login");
  };

  return (
    <main className="app-shell">
      <header className="top">
        <h2>Officer Dashboard</h2>
        <button className="btn secondary" onClick={logout}>Logout</button>
      </header>
      <section className="card">
        <div className="top">
          <h3>All Applications</h3>
          <div className="pager-extra">
            <input className="field" style={{ maxWidth: 220 }} placeholder="Search" value={query} onChange={(e) => setQuery(e.target.value)} />
            <select className="field" value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
              <option value="lastModifiedDesc">Latest Updated</option>
              <option value="lastModifiedAsc">Oldest Updated</option>
              <option value="businessAsc">Business A-Z</option>
              <option value="businessDesc">Business Z-A</option>
            </select>
            <select className="field" value={pageSize} onChange={(e) => setPageSize(Number(e.target.value))}>
              {PAGE_SIZE_OPTIONS.map((n) => <option key={n} value={n}>{n} / page</option>)}
            </select>
          </div>
        </div>
        <div className="button-grid" style={{ gridTemplateColumns: "repeat(4,1fr)", marginBottom: 12 }}>
          <button className={`btn ${filter === "all" ? "" : "secondary"}`} onClick={() => setFilter("all")}>All</button>
          <button className={`btn ${filter === "needs-action" ? "" : "secondary"}`} onClick={() => setFilter("needs-action")}>Needs Action</button>
          <button className={`btn ${filter === "in-progress" ? "" : "secondary"}`} onClick={() => setFilter("in-progress")}>In Progress</button>
          <button className={`btn ${filter === "terminal" ? "" : "secondary"}`} onClick={() => setFilter("terminal")}>Completed</button>
        </div>
        <table>
          <thead><tr><th>Reference</th><th>Business</th><th>Status</th><th>Submission Round</th><th>Final Outcome</th><th>Action</th></tr></thead>
          <tbody>
            {paged.map((a) => (
              <tr key={a.id}>
                <td>{a.referenceNumber}</td>
                <td>{a.businessName}</td>
                <td><span className={`badge ${statusClass(a.statusLabel)}`}>{a.statusLabel}</span></td>
                <td>#{a.submissionRound}</td>
                <td>{a.internalStatus === "APPROVED" || a.internalStatus === "REJECTED" ? a.statusLabel : pendingOwnerByInternalStatus(a.internalStatus)}</td>
                <td>
                  {(() => {
                    const needsOfficerAction = NEEDS_ACTION.has(a.internalStatus);
                    return (
                  <button
                    className="btn secondary"
                    disabled={!needsOfficerAction}
                    onClick={() => navigate(`/officer/applications/${a.id}`)}
                    title={needsOfficerAction ? "Review application" : "No officer action required"}
                  >
                    Review
                  </button>
                    );
                  })()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="pager">
          <div className="pager-info">Showing {start}-{end} of {filtered.length}</div>
          <div className="pager-controls">
            <button className="pager-btn" disabled={currentPage === 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>Previous</button>
            {Array.from({ length: totalPages }, (_, i) => i + 1).map((p) => (
              <button
                key={p}
                className="pager-btn"
                aria-current={p === currentPage ? "page" : undefined}
                onClick={() => setPage(p)}
              >
                {p}
              </button>
            ))}
            <button className="pager-btn" disabled={currentPage === totalPages} onClick={() => setPage((p) => Math.min(totalPages, p + 1))}>Next</button>
            <input
              className="field"
              style={{ width: 90, padding: "6px 8px", fontSize: 12 }}
              placeholder="Go page"
              value={gotoPage}
              onChange={(e) => setGotoPage(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  const p = Number(gotoPage);
                  if (Number.isFinite(p) && p >= 1 && p <= totalPages) setPage(p);
                }
              }}
            />
            <button
              className="pager-btn"
              onClick={() => {
                const p = Number(gotoPage);
                if (Number.isFinite(p) && p >= 1 && p <= totalPages) setPage(p);
              }}
            >
              Go
            </button>
          </div>
        </div>
      </section>
    </main>
  );
}
