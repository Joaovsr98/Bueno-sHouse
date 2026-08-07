import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthContext';
import { api, ApiError } from '../lib/api';

type KitchenSectorResponse = { id: number; unitId: number; name: string };

type KitchenTaskResponse = {
  orderItemId: number;
  orderId: number;
  orderNumber: number;
  productName: string;
  quantity: number;
  notes: string | null;
  status: string;
  startedAt: string | null;
  completedAt: string | null;
  orderCreatedAt: string;
};

const STATUS_LABELS: Record<string, string> = {
  PENDENTE: 'Pendente',
  ENVIADO: 'Aguardando início',
  EM_PREPARO: 'Em preparo',
  PRONTO: 'Pronto',
  ENTREGUE: 'Entregue',
  CANCELADO: 'Cancelado',
};

const STATUS_STYLES: Record<string, string> = {
  ENVIADO: 'border-amber-800 bg-amber-950',
  EM_PREPARO: 'border-blue-800 bg-blue-950',
  PRONTO: 'border-green-800 bg-green-950',
};

function elapsedMinutes(iso: string) {
  return Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
}

export function KitchenPage() {
  const { unitId } = useAuth();
  const queryClient = useQueryClient();
  const [sectorId, setSectorId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const sectorsQuery = useQuery({
    queryKey: ['kitchen-sectors'],
    queryFn: () => api.get<KitchenSectorResponse[]>('/kitchen-sectors'),
  });

  const unitSectors = (sectorsQuery.data ?? []).filter((s) => s.unitId === unitId);

  useEffect(() => {
    if (sectorId === null && unitSectors.length > 0) setSectorId(unitSectors[0].id);
  }, [sectorId, unitSectors]);

  const tasksQuery = useQuery({
    queryKey: ['kitchen-tasks', sectorId],
    queryFn: () => api.get<KitchenTaskResponse[]>(`/kitchen/tasks?sectorId=${sectorId}`),
    enabled: sectorId != null,
    refetchInterval: 15000,
  });

  const startTask = useMutation({
    mutationFn: (orderItemId: number) => api.post<KitchenTaskResponse>(`/kitchen/tasks/${orderItemId}/start`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['kitchen-tasks', sectorId] }),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao iniciar preparo'),
  });

  const completeTask = useMutation({
    mutationFn: (orderItemId: number) => api.post<KitchenTaskResponse>(`/kitchen/tasks/${orderItemId}/complete`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['kitchen-tasks', sectorId] }),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao concluir item'),
  });

  const markUnavailable = useMutation({
    mutationFn: ({ orderItemId, reason }: { orderItemId: number; reason: string }) =>
      api.post<KitchenTaskResponse>(`/kitchen/tasks/${orderItemId}/unavailable`, { reason }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['kitchen-tasks', sectorId] }),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao marcar indisponível'),
  });

  if (unitId == null) {
    return <div className="p-6 text-sm text-neutral-400">Carregando unidade...</div>;
  }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-lg font-semibold">Painel da Cozinha</h1>

        {unitSectors.length > 0 && (
          <select
            value={sectorId ?? ''}
            onChange={(e) => setSectorId(Number(e.target.value))}
            className="rounded-lg bg-neutral-900 border border-neutral-700 px-3 py-1.5 text-sm text-neutral-300"
          >
            {unitSectors.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        )}
      </div>

      {unitSectors.length === 0 && !sectorsQuery.isLoading && (
        <p className="text-sm text-neutral-500">Nenhum setor de cozinha cadastrado para esta unidade.</p>
      )}

      {error && (
        <div className="mb-4 rounded-lg bg-red-950 border border-red-800 p-3 text-sm text-red-300 flex justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-400">×</button>
        </div>
      )}

      {tasksQuery.data?.length === 0 && (
        <p className="text-sm text-neutral-500">Nenhum item pendente neste setor.</p>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        {tasksQuery.data?.map((task) => (
          <div
            key={task.orderItemId}
            className={`rounded-xl border p-4 flex flex-col gap-2 ${
              STATUS_STYLES[task.status] ?? 'border-neutral-800 bg-neutral-900'
            }`}
          >
            <div className="flex items-start justify-between">
              <span className="text-sm font-semibold">Pedido #{task.orderNumber}</span>
              <span className="text-xs text-neutral-400">{elapsedMinutes(task.orderCreatedAt)} min</span>
            </div>
            <p className="text-sm">{task.quantity}x {task.productName}</p>
            {task.notes && <p className="text-xs text-neutral-400 italic">{task.notes}</p>}
            <span className="text-xs uppercase tracking-wide text-neutral-400">
              {STATUS_LABELS[task.status] ?? task.status}
            </span>

            <div className="flex gap-2 mt-2">
              {task.status === 'ENVIADO' && (
                <button
                  onClick={() => startTask.mutate(task.orderItemId)}
                  className="flex-1 text-xs rounded-lg bg-neutral-100 text-neutral-900 py-1.5 font-medium"
                >
                  Iniciar
                </button>
              )}
              {task.status === 'EM_PREPARO' && (
                <button
                  onClick={() => completeTask.mutate(task.orderItemId)}
                  className="flex-1 text-xs rounded-lg bg-neutral-100 text-neutral-900 py-1.5 font-medium"
                >
                  Concluir
                </button>
              )}
              {['ENVIADO', 'EM_PREPARO'].includes(task.status) && (
                <button
                  onClick={() => {
                    const reason = window.prompt('Motivo da indisponibilidade:');
                    if (reason) markUnavailable.mutate({ orderItemId: task.orderItemId, reason });
                  }}
                  className="text-xs rounded-lg border border-current/30 px-2 py-1.5"
                >
                  Indisponível
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
