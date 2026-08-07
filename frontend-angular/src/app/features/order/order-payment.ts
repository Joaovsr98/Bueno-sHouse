import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { OrderBalanceResponse, CashRegisterResponse } from '../../core/models';
import { currency } from '../../core/format';

const PAYMENT_METHODS = [
  { value: 'DINHEIRO', label: 'Dinheiro' },
  { value: 'CARTAO_CREDITO', label: 'Cartão de crédito' },
  { value: 'CARTAO_DEBITO', label: 'Cartão de débito' },
  { value: 'PIX', label: 'Pix' },
];

@Component({
  selector: 'app-order-payment',
  imports: [FormsModule],
  templateUrl: './order-payment.html',
})
export class OrderPayment {
  private readonly api = inject(ApiService);

  orderId = input.required<number>();
  unitId = input.required<number>();
  errored = output<string>();

  readonly currency = currency;
  readonly methods = PAYMENT_METHODS;

  balance = signal<OrderBalanceResponse | null>(null);
  method = 'PIX';
  amount = '';
  saving = signal(false);

  constructor() {
    // orderId e input; carrega o saldo assim que o componente monta.
    queueMicrotask(() => this.loadBalance());
  }

  loadBalance(): void {
    this.api.get<OrderBalanceResponse>(`/payments/balance?orderId=${this.orderId()}`).subscribe({
      next: (b) => this.balance.set(b),
      error: (e) => this.errored.emit(apiErrorMessage(e, 'Erro ao carregar saldo')),
    });
  }

  async register(): Promise<void> {
    if (!this.amount) return;
    this.saving.set(true);
    try {
      let cashRegisterId: number | null = null;
      if (this.method === 'DINHEIRO') {
        try {
          const reg = await firstValueFrom(
            this.api.get<CashRegisterResponse>(`/cash-registers/current?unitId=${this.unitId()}`),
          );
          cashRegisterId = reg.id;
        } catch {
          this.errored.emit('Nenhum caixa aberto — abra o caixa antes de receber em dinheiro.');
          this.saving.set(false);
          return;
        }
      }
      await firstValueFrom(
        this.api.post('/payments', {
          orderId: this.orderId(),
          method: this.method,
          amount: Number(this.amount),
          cashRegisterId,
        }),
      );
      this.amount = '';
      this.loadBalance();
    } catch (e) {
      this.errored.emit(apiErrorMessage(e, 'Erro ao registrar pagamento'));
    } finally {
      this.saving.set(false);
    }
  }
}
