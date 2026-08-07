import { Component, inject, signal, effect, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { CashRegisterResponse, CashMovementResponse } from '../../core/models';
import { currency } from '../../core/format';

const MOVEMENT_LABELS: Record<string, string> = {
  ENTRADA: 'Entrada',
  SAIDA: 'Saída',
  SANGRIA: 'Sangria',
  SUPRIMENTO: 'Suprimento',
};

@Component({
  selector: 'app-cash-register',
  imports: [FormsModule],
  templateUrl: './cash-register.html',
})
export class CashRegister {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly unitId = this.auth.unitId;
  readonly currency = currency;
  readonly movementLabels = MOVEMENT_LABELS;

  register = signal<CashRegisterResponse | null>(null);
  movements = signal<CashMovementResponse[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  openingBalance = '0';
  closingBalance = '';
  showCloseForm = signal(false);
  movementType = 'SANGRIA';
  movementAmount = '';
  movementReason = '';

  constructor() {
    effect(() => {
      const u = this.unitId();
      if (u == null) return;
      untracked(() => this.loadCurrent());
    });
  }

  private loadCurrent(): void {
    this.loading.set(true);
    this.api.get<CashRegisterResponse>(`/cash-registers/current?unitId=${this.unitId()}`).subscribe({
      next: (r) => {
        this.register.set(r);
        this.loading.set(false);
        this.loadMovements();
      },
      error: (e) => {
        // 404 = nenhum caixa aberto (nao e erro de tela)
        if (e?.status === 404) {
          this.register.set(null);
        } else {
          this.error.set(apiErrorMessage(e, 'Erro ao carregar caixa'));
        }
        this.loading.set(false);
      },
    });
  }

  private loadMovements(): void {
    const reg = this.register();
    if (!reg) return;
    this.api.get<CashMovementResponse[]>(`/cash-registers/${reg.id}/movements`).subscribe({
      next: (m) => this.movements.set(m),
      error: () => this.movements.set([]),
    });
  }

  openRegister(): void {
    this.api
      .post<CashRegisterResponse>('/cash-registers', {
        unitId: this.unitId(),
        openingBalance: Number(this.openingBalance),
      })
      .subscribe({
        next: () => this.loadCurrent(),
        error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao abrir caixa')),
      });
  }

  closeRegister(): void {
    const reg = this.register();
    if (!reg) return;
    this.api
      .post<CashRegisterResponse>(`/cash-registers/${reg.id}/close`, {
        closingBalance: Number(this.closingBalance),
      })
      .subscribe({
        next: () => {
          this.showCloseForm.set(false);
          this.closingBalance = '';
          this.loadCurrent();
        },
        error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao fechar caixa')),
      });
  }

  registerMovement(): void {
    const reg = this.register();
    if (!reg || !this.movementAmount) return;
    this.api
      .post<CashMovementResponse>(`/cash-registers/${reg.id}/movements`, {
        type: this.movementType,
        amount: Number(this.movementAmount),
        reason: this.movementReason || null,
      })
      .subscribe({
        next: () => {
          this.movementAmount = '';
          this.movementReason = '';
          this.loadMovements();
        },
        error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao registrar movimento')),
      });
  }
}
