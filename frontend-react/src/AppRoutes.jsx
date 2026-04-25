import { Navigate, Route, Routes } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import OfficerDashboardPage from "./pages/OfficerDashboardPage";
import OperatorDashboardPage from "./pages/OperatorDashboardPage";
import OperatorSubmitPage from "./pages/OperatorSubmitPage";
import OperatorApplicationPage from "./pages/OperatorApplicationPage";
import OfficerApplicationPage from "./pages/OfficerApplicationPage";
import OfficerChecklistPage from "./pages/OfficerChecklistPage";

function Guard({ role, children }) {
  // Keep auth checks in one place so route-level role enforcement is consistent.
  const token = sessionStorage.getItem("token") || localStorage.getItem("token");
  const userRaw = sessionStorage.getItem("user") || localStorage.getItem("user") || "null";
  const user = JSON.parse(userRaw);
  if (!token || !user) return <Navigate to="/login" replace />;
  if (role && user.role !== role) {
    return <Navigate to={user.role === "OFFICER" ? "/officer/dashboard" : "/operator/dashboard"} replace />;
  }
  return children;
}

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/officer/dashboard" element={<Guard role="OFFICER"><OfficerDashboardPage /></Guard>} />
      <Route path="/officer/applications/:id" element={<Guard role="OFFICER"><OfficerApplicationPage /></Guard>} />
      <Route path="/officer/applications/:id/checklist" element={<Guard role="OFFICER"><OfficerChecklistPage /></Guard>} />
      <Route path="/operator/dashboard" element={<Guard role="OPERATOR"><OperatorDashboardPage /></Guard>} />
      <Route path="/operator/submit" element={<Guard role="OPERATOR"><OperatorSubmitPage /></Guard>} />
      <Route path="/operator/applications/:id" element={<Guard role="OPERATOR"><OperatorApplicationPage /></Guard>} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}
