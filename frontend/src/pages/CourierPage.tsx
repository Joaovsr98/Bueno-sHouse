import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../lib/api';

type DeliveryResponse = {
  id: number;
  orderId: number;
  courierId: number | null;
  addressSnapshot: string;
  neighborhoodSnapshot: string;
  fee: number;
  estimatedMinutes: number | null;
  status: string;
  receivedByName: string | null;
};

const STATUS_LABELS: Record<string, string> = {
  AGUARDANDO_ENTREGADOR: 'Aguardando entregador',
  ACEITA: 'Aceita',
  RETIRADA_NO_RESTAURANTE: 'Retirada no restaurante',
  EM_ROTA: 'Em rota',
  ENTREGUE: 'Entregue',
  NAO_ENTREGUE: 'Não entregue',
  DEVOLVIDA: 'Devolvida',
  CANCELADA: 'Cancelada',
};

function currency(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

export function CourierPage() {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [activeDelivery, setActiveDelivery] = useState<DeliveryResponse | null>(null);
  const [confirmationCode, setConfirmationCode] = useState('');
  const [receivedByName, setReceivedByName] = useState('');

  const availableQuery = useQuery({
    queryKey: ['deliveries-available'],
    queryFn: () => api.get<number[]>('/deliveries/available'),
    enabled: !activeDelivery,
    refetchInterval: 10000,
  });

  const acceptDelivery = useMutation({
    mutationFn: (id: number) => api.post<DeliveryResponse>(`/deliveries/${id}/accept`),
    onSuccess: (delivery) => {
      setActiveDelivery(delivery);
      queryClient.invalidateQueries({ queryKey: ['deliveries-available'] });
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao aceitar entrega'),
  });

  const pickup = useMutation({
    mutationFn: () => api.post<DeliveryResponse>(`/deliveries/${activeDelivery!.id}/pickup`),
    onSuccess: setActiveDelivery,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao confirmar retirada'),
  });

  const leave = useMutation({
    mutationFn: () => api.post<DeliveryResponse>(`/deliveries/${activeDelivery!.id}/leave`),
    onSuccess: setActiveDelivery,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao sair para entrega'),
  });

  const confirm = useMutation({
    mutationFn: () =>
      api.post<DeliveryResponse>(`/deliveries/${activeDelivery!.id}/confirm`, {
        confirmationCode,
        receivedByName,
      }),
    onSuccess: () => {
      setActiveDelivery(null);
      setConfirmationCode('');
      setReceivedByName('');
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao confirmar entrega'),
  });

  const reportFailure = useMutation({
    mutationFn: () => {
      const reason = window.prompt('Motivo da não entrega:');
      if (!reason) throw new ApiError(0, 'Motivo obrigatório');
      return api.post<DeliveryResponse>(`/deliveries/${activeDelivery!.id}/failure`, { reason });
    },
    onSuccess: (delivery) => setActiveDelivery(delivery),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao registrar falha'),
  });

  return (
    <div className="p-6 max-w-xl">
      <h1 className="text-lg font-semibold mb-4">Área do Motoboy</h1>

      {error && (
        <div className="mb-4 rounded-lg bg-red-950 border border-red-800 p-3 text-sm text-red-300 flex justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-400">×</button>
        </div>
      )}

      {!activeDelivery && (
        <>
          <h2 className="text-sm font-semibold mb-3">Entregas disponíveis</h2>
          {availableQuery.data?.length === 0 && (
            <p className="text-sm text-neutral-500">Nenhuma entrega aguardando entregador no momento.</p>
          )}
          <div className="space-y-2">
            {availableQuery.data?.map((id) => (
              <div
                key={id}
                className="rounded-xl border border-neutral-800 bg-neutral-900 p-4 flex items-center justify-between"
              >
                <span className="text-sm">Entrega #{id}</span>
                <button
                  onClick={() => acceptDelivery.mutate(id)}
                  disabled={acceptDelivery.isPending}
                  className="rounded-lg bg-neutral-100 text-neutral-900 px-3 py-1.5 text-xs font-medium disabled:opacity-50"
                >
                  Aceitar
                </button>
              </div>
            ))}
          </div>
        </>
      )}

      {activeDelivery && (
        <div className="rounded-xl border border-neutral-800 bg-neutral-900 p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold">Entrega #{activeDelivery.id} — Pedido #{activeDelivery.orderId}</h2>
            <span className="text-xs uppercase tracking-wide text-neutral-400">
              {STATUS_LABELS[activeDelivery.status] ?? activeDelivery.status}
            </span>
          </div>

          <div className="text-sm text-neutral-300 space-y-1">
            <p>{activeDelivery.addressSnapshot}</p>
            <p className="text-neutral-500">{activeDelivery.neighborhoodSnapshot}</p>
            <p className="text-neutral-500">
              Taxa: {currency(activeDelivery.fee)}
              {activeDelivery.estimatedMinutes ? ` · ~${activeDelivery.estimatedMinutes} min` : ''}
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {activeDelivery.status === 'ACEITA' && (
              <button
                onClick={() => pickup.mutate()}
                className="text-xs rounded-lg bg-neutral-100 text-neutral-900 px-3 py-1.5 font-medium"
              >
                Confirmar retirada no restaurante
              </button>
            )}
            {activeDelivery.status === 'RETIRADA_NO_RESTAURANTE' && (
              <button
                onClick={() => leave.mutate()}
                className="text-xs rounded-lg bg-neutral-100 text-neutral-900 px-3 py-1.5 font-medium"
              >
                Saí para entrega
              </button>
            )}
            {['EM_ROTA', 'NAO_ENTREGUE'].includes(activeDelivery.status) && (
              <button
                onClick={() => reportFailure.mutate()}
                className="text-xs rounded-lg border border-neutral-700 px-3 py-1.5 text-neutral-300"
              >
                Registrar não entrega
              </button>
            )}
          </div>

          {activeDelivery.status === 'EM_ROTA' && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                confirm.mutate();
              }}
              className="space-y-2 border-t border-neutral-800 pt-4"
            >
              <div className="space-y-1">
                <label className="text-xs text-neutral-400">Código de confirmação</label>
                <input
                  required
                  value={confirmationCode}
                  onChange={(e) => setConfirmationCode(e.target.value)}
                  className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
                />
              </div>
              <div className="space-y-1">
                <label className="text-xs text-neutral-400">Nome de quem recebeu</label>
                <input
                  required
                  value={receivedByName}
                  onChange={(e) => setReceivedByName(e.target.value)}
                  className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
                />
              </div>
              <button
                type="submit"
                disabled={confirm.isPending}
                className="rounded-lg bg-neutral-100 text-neutral-900 px-4 py-2 text-sm font-medium disabled:opacity-50"
              >
                Confirmar entrega
              </button>
            </form>
          )}

          <button
            onClick={() => setActiveDelivery(null)}
            className="text-xs text-neutral-500 hover:text-neutral-300"
          >
            ← Ver entregas disponíveis
          </button>
        </div>
      )}
    </div>
  );
}
