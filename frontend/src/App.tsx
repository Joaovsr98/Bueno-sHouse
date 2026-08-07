import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from './LoginPage';
import { AppLayout } from './layout/AppLayout';
import { CatalogPage } from './pages/CatalogPage';
import { TablesPage } from './pages/TablesPage';
import { ServicePage } from './pages/ServicePage';
import { OrderPage } from './pages/OrderPage';
import { KitchenPage } from './pages/KitchenPage';
import { CashRegisterPage } from './pages/CashRegisterPage';
import { CourierPage } from './pages/CourierPage';
import { useAuth } from './auth/AuthContext';

function defaultRouteForRole(profileName: string | undefined) {
  switch (profileName) {
    case 'MOTOBOY':
      return '/motoboy';
    case 'COZINHA':
      return '/cozinha';
    case 'CAIXA':
      return '/caixa';
    default:
      return '/mesas';
  }
}

function ProtectedArea() {
  const { token } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  return <AppLayout />;
}

function LoginRoute() {
  const { token, user, login } = useAuth();
  if (token) return <Navigate to={defaultRouteForRole(user?.profileName)} replace />;
  return <LoginPage onLoginSuccess={login} />;
}

function HomeRedirect() {
  const { user } = useAuth();
  return <Navigate to={defaultRouteForRole(user?.profileName)} replace />;
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginRoute />} />
      <Route element={<ProtectedArea />}>
        <Route path="/catalogo" element={<CatalogPage />} />
        <Route path="/mesas" element={<TablesPage />} />
        <Route path="/atendimento/:serviceId" element={<ServicePage />} />
        <Route path="/atendimento/:serviceId/comanda/:commandId" element={<OrderPage />} />
        <Route path="/cozinha" element={<KitchenPage />} />
        <Route path="/caixa" element={<CashRegisterPage />} />
        <Route path="/motoboy" element={<CourierPage />} />
        <Route path="/" element={<HomeRedirect />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
