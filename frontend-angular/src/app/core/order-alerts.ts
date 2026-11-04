/** Aviso de uma nova fase do pedido, para o cliente que esta esperando. */
export interface OrderAlert {
  icon: string;
  title: string;
  body: string;
}

/**
 * O que dizer ao cliente quando o pedido muda de fase. Na retirada no balcao nao ha
 * motoboy: "pronto" ja e a hora de buscar. null = fase que nao merece aviso.
 */
export function orderAlert(status: string, channel: string, deliveryCode: string | null): OrderAlert | null {
  const pickup = channel === 'RETIRADA';
  switch (status) {
    case 'ENVIADO_PARA_COZINHA':
      return { icon: '✅', title: 'Pedido confirmado!', body: 'A cozinha já recebeu o seu pedido.' };
    case 'EM_PREPARO':
      return { icon: '🔥', title: 'Na chapa!', body: 'Seu lanche começou a ser preparado.' };
    case 'PARCIALMENTE_PRONTO':
      return { icon: '⏳', title: 'Quase pronto', body: 'Falta pouco para o seu pedido ficar pronto.' };
    case 'PRONTO':
    case 'AGUARDANDO_RETIRADA':
      return pickup
        ? { icon: '🛍️', title: 'Pronto para retirar!', body: 'Seu pedido está esperando no balcão, na Rua Pais Leme.' }
        : { icon: '🍔', title: 'Pedido pronto!', body: 'Já já um motoboy sai com ele.' };
    case 'AGUARDANDO_ENTREGADOR':
      return { icon: '🍔', title: 'Pedido pronto!', body: 'Esperando o motoboy buscar na cozinha.' };
    case 'SAIU_PARA_ENTREGA':
      return {
        icon: '🛵',
        title: 'Saiu para entrega!',
        body: deliveryCode ? `Tenha o código ${deliveryCode} em mãos para o motoboy.` : 'O motoboy já está a caminho.',
      };
    case 'ENTREGUE':
    case 'FINALIZADO':
      return pickup
        ? { icon: '😋', title: 'Pedido retirado', body: 'Bom apetite! Obrigado por pedir na Bueno’s House.' }
        : { icon: '😋', title: 'Pedido entregue!', body: 'Bom apetite! Obrigado por pedir na Bueno’s House.' };
    case 'CANCELADO':
      return { icon: '⚠️', title: 'Pedido cancelado', body: 'Se tiver dúvida, fale com a gente pelo telefone da loja.' };
    default:
      return null;
  }
}
