import { Component, inject, signal, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { DeliveryResponse } from '../../core/models';
import { currency } from '../../core/format';

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

@Component({
  selector: 'app-courier',
  imports: [FormsModule],
  templateUrl: './courier.html',
})
export class Courier implements OnDestroy {
  private readonly api = inject(ApiService);

  readonly currency = currency;
  readonly statusLabels = STATUS_LABELS;

  available = signal<number[]>([]);
  active = signal<DeliveryResponse | null>(null);
  error = signal<string | null>(null);
  confirmationCode = '';
  receivedByName = '';

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.loadAvailable();
    this.pollHandle = setInterval(() => {
      if (!this.active()) this.loadAvailable();
    }, 10000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
  }

  loadAvailable(): void {
    this.api.get<number[]>('/deliveries/available').subscribe({
      next: (ids) => this.available.set(ids),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar entregas')),
    });
  }

  accept(id: number): void {
    this.api.post<DeliveryResponse>(`/deliveries/${id}/accept`).subscribe({
      next: (d) => {
        this.active.set(d);
        this.loadAvailable();
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao aceitar entrega')),
    });
  }

  pickup(): void {
    this.api.post<DeliveryResponse>(`/deliveries/${this.active()!.id}/pickup`).subscribe({
      next: (d) => this.active.set(d),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao confirmar retirada')),
    });
  }

  leave(): void {
    this.api.post<DeliveryResponse>(`/deliveries/${this.active()!.id}/leave`).subscribe({
      next: (d) => this.active.set(d),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao sair para entrega')),
    });
  }

  confirm(): void {
    this.api
      .post<DeliveryResponse>(`/deliveries/${this.active()!.id}/confirm`, {
        confirmationCode: this.confirmationCode,
        receivedByName: this.receivedByName,
      })
      .subscribe({
        next: () => {
          this.active.set(null);
          this.confirmationCode = '';
          this.receivedByName = '';
          this.loadAvailable();
        },
        error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao confirmar entrega')),
      });
  }

  reportFailure(): void {
    const reason = window.prompt('Motivo da não entrega:');
    if (!reason) return;
    this.api.post<DeliveryResponse>(`/deliveries/${this.active()!.id}/failure`, { reason }).subscribe({
      next: (d) => this.active.set(d),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao registrar falha')),
    });
  }

  canReportFailure(status: string): boolean {
    return status === 'EM_ROTA' || status === 'NAO_ENTREGUE';
  }
}
