import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import AppLayout from './layout/AppLayout';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import DatabasesPage from './pages/DatabasesPage';
import QueryAnalyzerPage from './pages/QueryAnalyzerPage';
import CopilotPage from './pages/CopilotPage';
import ReportsPage from './pages/ReportsPage';
import ComingSoonPage from './pages/ComingSoonPage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route element={<ProtectedRoute />}>
            <Route path="/app" element={<AppLayout />}>
              <Route index element={<Navigate to="/app/dashboard" replace />} />
              <Route path="dashboard" element={<DashboardPage />} />
              <Route path="databases" element={<DatabasesPage />} />
              <Route path="analyzer" element={<QueryAnalyzerPage />} />
              <Route path="copilot" element={<CopilotPage />} />
              <Route path="reports" element={<ReportsPage />} />
              <Route path="settings" element={<ComingSoonPage title="Settings" module="Module 8" />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
