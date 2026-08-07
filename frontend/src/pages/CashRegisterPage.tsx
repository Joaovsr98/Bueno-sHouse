import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthContext';
import { api, ApiError } from '../lib/api';

type CashRegisterResponse = {
  id: number;
  unitId: number;
  openingBalance: number;
  closingBalance: number | null;
  expectedBalance: number | null;
  difference: number | null;
  openedAt: string;
  closedAt: string | null;
  open: boolean;
};

type CashMovementResponse = {
  id: number;
  type: string;
  amount: number;
  reason: string | null;
  createdAt: string;
};

const MOVEMENT_LABELS: Record<string, string> = {
  ENTRADA: 'Entrada',
  SAIDA: 'Saída',
  SANGRIA: 'Sangria',
  SUPRIMENTO: 'Suprimento',
};

function currency(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

export function CashRegisterPage() {
  const { unitId } = useAuth();
  const queryClient = useQueryClient();

  const [error, setError] = useState<string | null>(null);
  const [openingBalance, setOpeningBalance] = useState('0');
  const [closingBalance, setClosingBalance] = useState('');
  const [showCloseForm, setShowCloseForm] = useState(false);
  const [movementType, setMovementType] = useState('SANGRIA');
  const [movementAmount, setMovementAmount] = useState('');
  const [movementReason, setMovementReason] = useState('');

  const registerQuery = useQuery({
    queryKey: ['cash-register-current', unitId],
    queryFn: async () => {
      try {
        return await api.get<CashRegisterResponse>(`/cash-registers/current?unitId=${unitId}`);
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) return null;
        throw err;
      }
    },
    enabled: unitId != null,
  });

  const register = registerQuery.data ?? null;

  const movementsQuery = useQuery({
    queryKey: ['cash-movements', register?.id],
    queryFn: () => api.get<CashMovementResponse[]>(`/cash-registers/${register!.id}/movements`),
    enabled: register != null,
  });

  const openRegister = useMutation({
    mutationFn: () =>
      api.post<CashRegisterResponse>('/cash-registers', {
        unitId,
        openingBalance: Number(openingBalance),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cash-register-current', unitId] }),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao abrir caixa'),
  });

  const closeRegister = useMutation({
    mutationFn: () =>
      api.post<CashRegisterResponse>(`/cash-registers/${register!.id}/close`, {
        closingBalance: Number(closingBalance),
      }),
    onSuccess: () => {
      setShowCloseForm(false);
      setClosingBalance('');
      queryClient.invalidateQueries({ queryKey: ['cash-register-current', unitId] });
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao fechar caixa'),
  });

  const registerMovement = useMutation({
    mutationFn: () =>
      api.post<CashMovementResponse>(`/cash-registers/${register!.id}/movements`, {
        type: movementType,
        amount: Number(movementAmount),
        reason: movementReason || null,
      }),
    onSuccess: () => {
      setMovementAmount('');
      setMovementReason('');
      queryClient.invalidateQueries({ queryKey: ['cash-movements', register?.id] });
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao registrar movimento'),
  });

  if (unitId == null || registerQuery.isLoading) {
    return <div className="p-6 text-sm text-neutral-400">Carregando...</div>;
  }

  return (
    <div className="p-6 max-w-2xl">
      <h1 className="text-lg font-semibold mb-4">Caixa</h1>

      {error && (
        <div className="mb-4 rounded-lg bg-red-950 border border-red-800 p-3 text-sm text-red-300 flex justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-400">×</button>
        </div>
      )}

      {!register && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            openRegister.mutate();
          }}
          className="rounded-xl border border-neutral-800 bg-neutral-900 p-6 space-y-4"
        >
          <p className="text-sm text-neutral-400">Nenhum caixa aberto nesta unidade.</p>
          <div className="space-y-1">
            <label className="text-xs text-neutral-400">Saldo inicial (R$)</label>
            <input
              required
              type="number"
              step="0.01"
              min="0"
              value={openingBalance}
              onChange={(e) => setOpeningBalance(e.target.value)}
              className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
            />
          </div>
          <button
            type="submit"
            disabled={openRegister.isPending}
            className="rounded-lg bg-neutral-100 text-neutral-900 px-4 py-2 text-sm font-medium disabled:opacity-50"
          >
            Abrir caixa
          </button>
        </form>
      )}

      {register && (
        <div className="space-y-6">
          <div className="rounded-xl border border-neutral-800 bg-neutral-900 p-6">
            <div className="flex items-center justify-between mb-3">
              <span className="text-sm font-semibold">Caixa #{register.id} — aberto</span>
              <button
                onClick={() => setShowCloseForm((v) => !v)}
                className="text-xs rounded-lg border border-neutral-700 px-3 py-1.5 text-neutral-300 hover:bg-neutral-800"
              >
                Fechar caixa
              </button>
            </div>
            <p className="text-sm text-neutral-400">Saldo inicial: {currency(register.openingBalance)}</p>

            {showCloseForm && (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  closeRegister.mutate();
                }}
                className="mt-4 flex gap-2 items-end"
              >
                <div className="space-y-1 flex-1">
                  <label className="text-xs text-neutral-400">Saldo contado ao fechar (R$)</label>
                  <input
                    required
                    type="number"
                    step="0.01"
                    min="0"
                    value={closingBalance}
                    onChange={(e) => setClosingBalance(e.target.value)}
                    className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
                  />
                </div>
                <button
                  type="submit"
                  disabled={closeRegister.isPending}
                  className="rounded-lg bg-neutral-100 text-neutral-900 px-4 py-2 text-sm font-medium disabled:opacity-50"
                >
                  Confirmar
                </button>
              </form>
            )}
          </div>

          <div className="rounded-xl border border-neutral-800 bg-neutral-900 p-6">
            <h2 className="text-sm font-semibold mb-3">Movimentos (sangria / suprimento)</h2>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (movementAmount) registerMovement.mutate();
              }}
              className="flex flex-wrap gap-2 items-end mb-4"
            >
              <div className="space-y-1">
                <label className="text-xs text-neutral-400">Tipo</label>
                <select
                  value={movementType}
                  onChange={(e) => setMovementType(e.target.value)}
                  className="rounded-lg bg-neutral-800 border border-neutral-700 px-2 py-2 text-sm"
                >
                  <option value="SANGRIA">Sangria</option>
                  <option value="SUPRIMENTO">Suprimento</option>
                  <option value="ENTRADA">Entrada</option>
                  <option value="SAIDA">Saída</option>
                </select>
              </div>
              <div className="space-y-1">
                <label className="text-xs text-neutral-400">Valor (R$)</label>
                <input
                  required
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={movementAmount}
                  onChange={(e) => setMovementAmount(e.target.value)}
                  className="w-28 rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
                />
              </div>
              <div className="space-y-1 flex-1 min-w-[10rem]">
                <label className="text-xs text-neutral-400">Motivo</label>
                <input
                  value={movementReason}
                  onChange={(e) => setMovementReason(e.target.value)}
                  className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
                />
              </div>
              <button
                type="submit"
                disabled={registerMovement.isPending}
                className="rounded-lg bg-neutral-100 text-neutral-900 px-4 py-2 text-sm font-medium disabled:opacity-50"
              >
                Registrar
              </button>
            </form>

            <div className="space-y-1">
              {movementsQuery.data?.length === 0 && (
                <p className="text-sm text-neutral-500">Nenhum movimento registrado.</p>
              )}
              {movementsQuery.data?.map((m) => (
                <div key={m.id} className="flex justify-between text-sm py-1.5 border-t border-neutral-800 first:border-t-0">
                  <span className="text-neutral-300">
                    {MOVEMENT_LABELS[m.type] ?? m.type}
                    {m.reason ? ` — ${m.reason}` : ''}
                  </span>
                  <span className="text-neutral-400">{currency(m.amount)}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
