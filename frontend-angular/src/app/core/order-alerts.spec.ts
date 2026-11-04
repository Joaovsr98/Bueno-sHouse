import { orderAlert } from './order-alerts';

describe('orderAlert', () => {
  it('avisa cada fase do delivery, com o codigo quando sai para entrega', () => {
    expect(orderAlert('EM_PREPARO', 'DELIVERY', null)?.title).toBe('Na chapa!');
    expect(orderAlert('SAIU_PARA_ENTREGA', 'DELIVERY', '123456')?.body).toContain('123456');
    expect(orderAlert('FINALIZADO', 'DELIVERY', null)?.title).toBe('Pedido entregue!');
  });

  it('na retirada, pronto ja e a hora de buscar no balcao', () => {
    expect(orderAlert('PRONTO', 'RETIRADA', null)?.title).toBe('Pronto para retirar!');
    expect(orderAlert('FINALIZADO', 'RETIRADA', null)?.title).toBe('Pedido retirado');
  });

  it('fase sem aviso devolve null', () => {
    expect(orderAlert('RECEBIDO', 'DELIVERY', null)).toBeNull();
  });
});
