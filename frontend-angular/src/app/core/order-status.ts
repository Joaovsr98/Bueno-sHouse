/**
 * Nomes de status de pedido exibidos na tela. Internamente o pedido confirmado
 * fica em ENVIADO_PARA_COZINHA (a confirmacao e o envio para a cozinha), mas
 * para o usuario ele e "Confirmado", como no fluxo 9.2 do guia.
 */
export const ORDER_STATUS_LABELS: Record<string, string> = {
  RECEBIDO: 'Recebido',
  ENVIADO_PARA_COZINHA: 'Confirmado',
  EM_PREPARO: 'Em preparo',
  PARCIALMENTE_PRONTO: 'Quase pronto',
  PRONTO: 'Pronto',
  AGUARDANDO_RETIRADA: 'Aguardando retirada',
  AGUARDANDO_ENTREGADOR: 'Aguardando entregador',
  SAIU_PARA_ENTREGA: 'Saiu para entrega',
  ENTREGUE: 'Entregue',
  FINALIZADO: 'Finalizado',
  CANCELADO: 'Cancelado',
};

export const CHANNEL_LABELS: Record<string, string> = {
  SALAO: 'Salão',
  BALCAO: 'Balcão',
  RETIRADA: 'Retirada',
  DELIVERY: 'Delivery',
};

export function orderStatusLabel(status: string): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

/** Etapas da timeline do cliente (RF-007): RECEBIDO -> CONFIRMADO -> ... -> ENTREGUE. */
export const ORDER_TIMELINE = [
  { key: 'received', label: 'Recebido', statuses: ['RECEBIDO'] },
  { key: 'confirmed', label: 'Confirmado', statuses: ['ENVIADO_PARA_COZINHA'] },
  { key: 'preparing', label: 'Em preparo', statuses: ['EM_PREPARO', 'PARCIALMENTE_PRONTO'] },
  { key: 'ready', label: 'Pronto', statuses: ['PRONTO', 'AGUARDANDO_RETIRADA', 'AGUARDANDO_ENTREGADOR'] },
  { key: 'delivering', label: 'Saiu p/ entrega', statuses: ['SAIU_PARA_ENTREGA'] },
  { key: 'delivered', label: 'Entregue', statuses: ['ENTREGUE', 'FINALIZADO'] },
];
