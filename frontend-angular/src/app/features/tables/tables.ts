import { Component, inject, signal, effect, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ServiceResponse, TableResponse } from '../../core/models';

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

@Component({
  selector: 'app-tables',
  imports: [FormsModule],
  templateUrl: './tables.html',
})
export class Tables {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly unitId = this.auth.unitId;
  readonly statusStyles = STATUS_STYLES;
  readonly statusLabels = STATUS_LABELS;
  readonly manualTransitions = MANUAL_TRANSITIONS;

  tables = signal<TableResponse[]>([]);
  error = signal<string | null>(null);
  navigatingTableId = signal<number | null>(null);

  openingTable = signal<TableResponse | null>(null);
  partySize = '2';
  notes = '';
  newTableNumber = '';
  newTableCapacity = '4';

  constructor() {
    effect(() => {
      const u = this.unitId();
      if (u == null) return;
      untracked(() => this.loadTables());
    });
  }

  loadTables(): void {
    this.api.get<TableResponse[]>(`/tables?unitId=${this.unitId()}`).subscribe({
      next: (t) => this.tables.set(t),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar mesas')),
    });
  }

  createTable(): void {
    if (!this.newTableNumber.trim()) return;
    this.api
      .post<TableResponse>('/tables', {
        unitId: this.unitId(),
        number: this.newTableNumber,
        capacity: Number(this.newTableCapacity),
      })
      .subscribe({
        next: () => {
          this.newTableNumber = '';
          this.loadTables();
        },
        error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao criar mesa')),
      });
  }

  changeStatus(table: TableResponse, status: string): void {
    this.api.post<TableResponse>(`/tables/${table.id}/transitions`, { status }).subscribe({
      next: () => this.loadTables(),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao alterar status da mesa')),
    });
  }

  startOpening(table: TableResponse): void {
    this.openingTable.set(table);
    this.partySize = '2';
    this.notes = '';
  }

  confirmOpen(): void {
    const table = this.openingTable();
    if (!table) return;
    this.api
      .post<ServiceResponse>('/services', {
        tableId: table.id,
        partySize: Number(this.partySize),
        notes: this.notes || null,
      })
      .subscribe({
        next: (service) => {
          this.openingTable.set(null);
          this.notes = '';
          this.loadTables();
          this.router.navigate(['/atendimento', service.id], { state: { service } });
        },
        error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao abrir atendimento')),
      });
  }

  async goToOccupiedTable(table: TableResponse): Promise<void> {
    this.navigatingTableId.set(table.id);
    this.error.set(null);
    try {
      const services = await firstValueFrom(
        this.api.get<ServiceResponse[]>(`/services?tableId=${table.id}`),
      );
      const active = services.find((s) => ACTIVE_SERVICE_STATUSES.has(s.status));
      if (!active) {
        this.error.set('Nenhum atendimento ativo encontrado para esta mesa.');
        return;
      }
      this.router.navigate(['/atendimento', active.id], { state: { service: active } });
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erro ao buscar atendimento da mesa'));
    } finally {
      this.navigatingTableId.set(null);
    }
  }
}
