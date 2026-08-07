import { useEffect } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthContext';
import { api } from '../lib/api';

type UnitResponse = {
  id: number;
  restaurantId: number;
  restaurantName: string;
  name: string;
  address: string;
  phone: string;
  timezone: string;
  active: boolean;
};

const navItems = [
  { to: '/mesas', label: 'Mesas' },
  { to: '/cozinha', label: 'Cozinha' },
  { to: '/caixa', label: 'Caixa' },
  { to: '/catalogo', label: 'Catálogo' },
  { to: '/motoboy', label: 'Motoboy' },
];

export function AppLayout() {
  const { user, unitId, setUnitId, logout } = useAuth();
  const { data: units } = useQuery({
    queryKey: ['units'],
    queryFn: () => api.get<UnitResponse[]>('/units'),
  });

  useEffect(() => {
    if (!unitId && units && units.length > 0) {
      setUnitId(units[0].id);
    }
  }, [unitId, units, setUnitId]);

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex">
      <aside className="w-56 shrink-0 border-r border-neutral-800 flex flex-col">
        <div className="p-4 border-b border-neutral-800">
          <p className="text-sm font-semibold">Sistema Restaurante</p>
          <p className="text-xs text-neutral-500 mt-0.5">{user?.profileName}</p>
        </div>

        <nav className="flex-1 p-2 space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `block rounded-lg px-3 py-2 text-sm ${
                  isActive
                    ? 'bg-neutral-800 text-neutral-100'
                    : 'text-neutral-400 hover:bg-neutral-900 hover:text-neutral-200'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="p-3 border-t border-neutral-800 space-y-2">
          {units && units.length > 0 && (
            <select
              value={unitId ?? ''}
              onChange={(e) => setUnitId(Number(e.target.value))}
              className="w-full rounded-lg bg-neutral-900 border border-neutral-700 px-2 py-1.5 text-xs text-neutral-300"
            >
              {units.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name}
                </option>
              ))}
            </select>
          )}
          <button
            onClick={logout}
            className="w-full text-left text-xs text-neutral-500 hover:text-neutral-300 px-1"
          >
            Sair
          </button>
        </div>
      </aside>

      <main className="flex-1 min-w-0">
        <Outlet />
      </main>
    </div>
  );
}
