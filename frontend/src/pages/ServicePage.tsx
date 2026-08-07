import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../lib/api';

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

type CommandResponse = {
  id: number;
  serviceId: number;
  status: string;
  openedAt: string;
  closedAt: string | null;
};

const SERVICE_STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberto',
  IN_SERVICE: 'Em atendimento',
  AWAITING_CLOSURE: 'Aguardando encerramento',
  CLOSED: 'Encerrado',
  CANCELLED: 'Cancelado',
};

const SERVICE_TRANSITIONS: Record<string, { to: string; label: string }[]> = {
  OPEN: [{ to: 'IN_SERVICE', label: 'Marcar em atendimento' }],
  IN_SERVICE: [{ to: 'AWAITING_CLOSURE', label: 'Pedir encerramento' }],
  AWAITING_CLOSURE: [
    { to: 'CLOSED', label: 'Encerrar atendimento' },
    { to: 'IN_SERVICE', label: 'Voltar para atendimento' },
  ],
};

const COMMAND_STATUS_LABELS: Record<string, string> = {
  ABERTA: 'Aberta',
  EM_ATENDIMENTO: 'Em atendimento',
  AGUARDANDO_PAGAMENTO: 'Aguardando pagamento',
  PARCIALMENTE_PAGA: 'Parcialmente paga',
  FECHADA: 'Fechada',
  CANCELADA: 'Cancelada',
};

const COMMAND_TRANSITIONS: Record<string, { to: string; label: string }[]> = {
  ABERTA: [{ to: 'EM_ATENDIMENTO', label: 'Iniciar atendimento' }],
  EM_ATENDIMENTO: [{ to: 'AGUARDANDO_PAGAMENTO', label: 'Enviar para pagamento' }],
  AGUARDANDO_PAGAMENTO: [{ to: 'FECHADA', label: 'Marcar como fechada' }],
};

export function ServicePage() {
  const { serviceId } = useParams<{ serviceId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [service, setService] = useState<ServiceResponse | null>(
    (location.state as { service?: ServiceResponse } | null)?.service ?? null,
  );

  const commandsQuery = useQuery({
    queryKey: ['commands', serviceId],
    queryFn: () => api.get<CommandResponse[]>(`/commands?serviceId=${serviceId}`),
    enabled: !!serviceId,
  });

  const openCommand = useMutation({
    mutationFn: () => api.post<CommandResponse>(`/commands?serviceId=${serviceId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['commands', serviceId] }),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao abrir comanda'),
  });

  const transitionCommand = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      api.post<CommandResponse>(`/commands/${id}/transitions`, { status }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['commands', serviceId] }),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao alterar status da comanda'),
  });

  const transitionService = useMutation({
    mutationFn: (status: string) => api.post<ServiceResponse>(`/services/${serviceId}/transitions`, { status }),
    onSuccess: (updated) => {
      setService(updated);
      if (updated.status === 'CLOSED') navigate('/mesas');
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao alterar status do atendimento'),
  });

  const status = service?.status;

  return (
    <div className="p-6 max-w-3xl">
      <button onClick={() => navigate('/mesas')} className="text-xs text-neutral-500 hover:text-neutral-300 mb-4">
        ← Voltar para mesas
      </button>

      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-lg font-semibold">Atendimento #{serviceId}</h1>
          {service && (
            <p className="text-sm text-neutral-400 mt-1">
              Mesa {service.tableNumber} · {service.partySize} pessoa(s)
            </p>
          )}
        </div>
        {status && (
          <span className="text-xs uppercase tracking-wide rounded-full border border-neutral-700 px-3 py-1 text-neutral-300">
            {SERVICE_STATUS_LABELS[status] ?? status}
          </span>
        )}
      </div>

      {error && (
        <div className="mb-4 rounded-lg bg-red-950 border border-red-800 p-3 text-sm text-red-300 flex justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-400">
            ×
          </button>
        </div>
      )}

      {status && SERVICE_TRANSITIONS[status]?.length > 0 && (
        <div className="flex gap-2 mb-6">
          {SERVICE_TRANSITIONS[status].map((t) => (
            <button
              key={t.to}
              onClick={() => transitionService.mutate(t.to)}
              className="text-xs rounded-lg border border-neutral-700 px-3 py-1.5 text-neutral-300 hover:bg-neutral-900"
            >
              {t.label}
            </button>
          ))}
        </div>
      )}

      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold">Comandas</h2>
        <button
          onClick={() => openCommand.mutate()}
          disabled={openCommand.isPending}
          className="rounded-lg bg-neutral-100 text-neutral-900 px-3 py-1.5 text-xs font-medium disabled:opacity-50"
        >
          + Nova comanda
        </button>
      </div>

      {commandsQuery.data?.length === 0 && (
        <p className="text-sm text-neutral-500">Nenhuma comanda aberta ainda.</p>
      )}

      <div className="space-y-2">
        {commandsQuery.data?.map((command) => (
          <div
            key={command.id}
            className="rounded-xl border border-neutral-800 bg-neutral-900 p-4 flex items-center justify-between"
          >
            <div>
              <p className="text-sm font-medium">Comanda #{command.id}</p>
              <p className="text-xs text-neutral-500 mt-0.5">
                {COMMAND_STATUS_LABELS[command.status] ?? command.status}
              </p>
            </div>
            <div className="flex gap-2">
              {!['FECHADA', 'CANCELADA'].includes(command.status) && (
                <button
                  onClick={() => navigate(`/atendimento/${serviceId}/comanda/${command.id}`)}
                  className="text-xs rounded-lg bg-neutral-100 text-neutral-900 px-3 py-1.5 font-medium"
                >
                  Fazer pedido
                </button>
              )}
              {(COMMAND_TRANSITIONS[command.status] ?? []).map((t) => (
                <button
                  key={t.to}
                  onClick={() => transitionCommand.mutate({ id: command.id, status: t.to })}
                  className="text-xs rounded-lg border border-neutral-700 px-3 py-1.5 text-neutral-300 hover:bg-neutral-800"
                >
                  {t.label}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
