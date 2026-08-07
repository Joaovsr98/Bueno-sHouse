import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { api, ApiError } from '../lib/api';

type TableResponse = {
  id: number;
  unitId: number;
  number: string;
  capacity: number;
  status: 'LIVRE' | 'OCUPADA' | 'RESERVADA' | 'AGUARDANDO_LIMPEZA' | 'BLOQUEADA';
};

type ServiceResponse = {
  id: number;
  tableId: number;
  tableNumber: string;
  partySize: number;
  status: string;
  notes: string | null;
  openedAt: string;
  closedAt: string | null;
};

const ACTIVE_SERVICE_STATUSES = new Set(['OPEN', 'IN_SERVICE', 'AWAITING_CLOSURE']);

const STATUS_STYLES: Record<TableResponse['status'], string> = {
  LIVRE: 'border-green-800 bg-green-950 text-green-300',
  OCUPADA: 'border-amber-800 bg-amber-950 text-amber-300',
  RESERVADA: 'border-blue-800 bg-blue-950 text-blue-300',
  AGUARDANDO_LIMPEZA: 'border-purple-800 bg-purple-950 text-purple-300',
  BLOQUEADA: 'border-red-800 bg-red-950 text-red-300',
};

const STATUS_LABELS: Record<TableResponse['status'], string> = {
  LIVRE: 'Livre',
  OCUPADA: 'Ocupada',
  RESERVADA: 'Reservada',
  AGUARDANDO_LIMPEZA: 'Aguardando limpeza',
  BLOQUEADA: 'Bloqueada',
};

const MANUAL_TRANSITIONS: Record<TableResponse['status'], { to: string; label: string }[]> = {
  LIVRE: [{ to: 'BLOQUEADA', label: 'Bloquear' }],
  OCUPADA: [{ to: 'BLOQUEADA', label: 'Bloquear' }],
  RESERVADA: [
    { to: 'LIVRE', label: 'Cancelar reserva' },
    { to: 'BLOQUEADA', label: 'Bloquear' },
  ],
  AGUARDANDO_LIMPEZA: [{ to: 'LIVRE', label: 'Liberar mesa' }],
  BLOQUEADA: [{ to: 'LIVRE', label: 'Desbloquear' }],
};

export function TablesPage() {
  const { unitId } = useAuth();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const [error, setError] = useState<string | null>(null);
  const [openingTable, setOpeningTable] = useState<TableResponse | null>(null);
  const [partySize, setPartySize] = useState('2');
  const [notes, setNotes] = useState('');
  const [newTableNumber, setNewTableNumber] = useState('');
  const [newTableCapacity, setNewTableCapacity] = useState('4');
  const [navigatingTableId, setNavigatingTableId] = useState<number | null>(null);

  const tablesQuery = useQuery({
    queryKey: ['tables', unitId],
    queryFn: () => api.get<TableResponse[]>(`/tables?unitId=${unitId}`),
    enabled: unitId != null,
  });

  const createTable = useMutation({
    mutationFn: () =>
      api.post<TableResponse>('/tables', {
        unitId,
        number: newTableNumber,
        capacity: Number(newTableCapacity),
      }),
    onSuccess: () => {
      setNewTableNumber('');
      queryClient.invalidateQueries({ queryKey: ['tables', unitId] });
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao criar mesa'),
  });

  const changeTableStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      api.post<TableResponse>(`/tables/${id}/transitions`, { status }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tables', unitId] }),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao alterar status da mesa'),
  });

  const openService = useMutation({
    mutationFn: () =>
      api.post<ServiceResponse>('/services', {
        tableId: openingTable!.id,
        partySize: Number(partySize),
        notes: notes || null,
      }),
    onSuccess: (service) => {
      queryClient.invalidateQueries({ queryKey: ['tables', unitId] });
      setOpeningTable(null);
      setNotes('');
      navigate(`/atendimento/${service.id}`, { state: { service } });
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao abrir atendimento'),
  });

  async function goToOccupiedTable(table: TableResponse) {
    setNavigatingTableId(table.id);
    setError(null);
    try {
      const services = await api.get<ServiceResponse[]>(`/services?tableId=${table.id}`);
      const active = services.find((s) => ACTIVE_SERVICE_STATUSES.has(s.status));
      if (!active) {
        setError('Nenhum atendimento ativo encontrado para esta mesa.');
        return;
      }
      navigate(`/atendimento/${active.id}`, { state: { service: active } });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Erro ao buscar atendimento da mesa');
    } finally {
      setNavigatingTableId(null);
    }
  }

  if (unitId == null) {
    return <div className="p-6 text-sm text-neutral-400">Carregando unidade...</div>;
  }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-lg font-semibold">Mapa de Mesas</h1>

        <form
          className="flex gap-2 items-center"
          onSubmit={(e) => {
            e.preventDefault();
            if (newTableNumber.trim()) createTable.mutate();
          }}
        >
          <input
            value={newTableNumber}
            onChange={(e) => setNewTableNumber(e.target.value)}
            placeholder="Número"
            className="w-24 rounded-lg bg-neutral-900 border border-neutral-700 px-2 py-1.5 text-xs outline-none focus:border-neutral-500"
          />
          <input
            value={newTableCapacity}
            onChange={(e) => setNewTableCapacity(e.target.value)}
            type="number"
            min="1"
            placeholder="Lugares"
            className="w-20 rounded-lg bg-neutral-900 border border-neutral-700 px-2 py-1.5 text-xs outline-none focus:border-neutral-500"
          />
          <button
            type="submit"
            disabled={createTable.isPending}
            className="rounded-lg bg-neutral-100 text-neutral-900 px-3 py-1.5 text-xs font-medium disabled:opacity-50"
          >
            + Mesa
          </button>
        </form>
      </div>

      {error && (
        <div className="mb-4 rounded-lg bg-red-950 border border-red-800 p-3 text-sm text-red-300 flex justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-400">
            ×
          </button>
        </div>
      )}

      {tablesQuery.data?.length === 0 && (
        <p className="text-sm text-neutral-500">Nenhuma mesa cadastrada nesta unidade.</p>
      )}

      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
        {tablesQuery.data?.map((table) => (
          <div
            key={table.id}
            className={`rounded-xl border p-4 flex flex-col gap-2 ${STATUS_STYLES[table.status]}`}
          >
            <div className="flex items-center justify-between">
              <span className="text-lg font-semibold">Mesa {table.number}</span>
              <span className="text-xs">{table.capacity} lug.</span>
            </div>
            <span className="text-xs uppercase tracking-wide">{STATUS_LABELS[table.status]}</span>

            <div className="mt-2 flex flex-col gap-1.5">
              {table.status === 'LIVRE' && (
                <button
                  onClick={() => {
                    setOpeningTable(table);
                    setPartySize('2');
                    setNotes('');
                  }}
                  className="text-xs rounded-lg bg-neutral-100 text-neutral-900 py-1.5 font-medium"
                >
                  Abrir atendimento
                </button>
              )}
              {table.status === 'OCUPADA' && (
                <button
                  onClick={() => goToOccupiedTable(table)}
                  disabled={navigatingTableId === table.id}
                  className="text-xs rounded-lg bg-neutral-100 text-neutral-900 py-1.5 font-medium disabled:opacity-50"
                >
                  {navigatingTableId === table.id ? 'Abrindo...' : 'Ver atendimento'}
                </button>
              )}
              {MANUAL_TRANSITIONS[table.status].map((t) => (
                <button
                  key={t.to}
                  onClick={() => changeTableStatus.mutate({ id: table.id, status: t.to })}
                  className="text-xs rounded-lg border border-current/30 py-1.5"
                >
                  {t.label}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>

      {openingTable && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-6 z-10">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              openService.mutate();
            }}
            className="w-full max-w-sm rounded-xl border border-neutral-800 bg-neutral-900 p-6 space-y-4"
          >
            <h2 className="text-sm font-semibold">Abrir atendimento — Mesa {openingTable.number}</h2>

            <div className="space-y-1">
              <label className="text-xs text-neutral-400">Número de pessoas</label>
              <input
                required
                type="number"
                min="1"
                value={partySize}
                onChange={(e) => setPartySize(e.target.value)}
                className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs text-neutral-400">Observações</label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={2}
                className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setOpeningTable(null)}
                className="text-sm text-neutral-400 px-3 py-1.5"
              >
                Cancelar
              </button>
              <button
                type="submit"
                disabled={openService.isPending}
                className="rounded-lg bg-neutral-100 text-neutral-900 px-4 py-1.5 text-sm font-medium disabled:opacity-50"
              >
                Abrir
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
