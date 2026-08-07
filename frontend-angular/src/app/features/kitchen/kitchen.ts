import { Component, inject, signal, effect, untracked, OnDestroy } from '@angular/core';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { KitchenSectorResponse, KitchenTaskResponse } from '../../core/models';
import { elapsedMinutes } from '../../core/format';

const STATUS_LABELS: Record<string, string> = {
  PENDENTE: 'Pendente',
  ENVIADO: 'Aguardando início',
  EM_PREPARO: 'Em preparo',
  PRONTO: 'Pronto',
  ENTREGUE: 'Entregue',
  CANCELADO: 'Cancelado',
};

const STATUS_STYLES: Record<string, string> = {
  ENVIADO: 'border-amber-800 bg-amber-950/60',
  EM_PREPARO: 'border-blue-800 bg-blue-950/60',
  PRONTO: 'border-green-800 bg-green-950/60',
};

@Component({
  selector: 'app-kitchen',
  imports: [],
  templateUrl: './kitchen.html',
})
export class Kitchen implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly unitId = this.auth.unitId;
  readonly statusLabels = STATUS_LABELS;
  readonly statusStyles = STATUS_STYLES;
  readonly elapsedMinutes = elapsedMinutes;

  sectors = signal<KitchenSectorResponse[]>([]);
  sectorId = signal<number | null>(null);
  tasks = signal<KitchenTaskResponse[]>([]);
  error = signal<string | null>(null);

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor() {
    effect(() => {
      const u = this.unitId();
      if (u == null) return;
      untracked(() => this.loadSectors());
    });
    // Auto-refresh das tarefas a cada 15s (equivalente ao refetchInterval do React).
    effect(() => {
      const s = this.sectorId();
      if (s == null) return;
      untracked(() => this.loadTasks());
    });
    this.pollHandle = setInterval(() => {
      if (this.sectorId() != null) this.loadTasks();
    }, 15000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
  }

  private loadSectors(): void {
    this.api.get<KitchenSectorResponse[]>('/kitchen-sectors').subscribe({
      next: (all) => {
        const mine = all.filter((s) => s.unitId === this.unitId());
        this.sectors.set(mine);
        if (this.sectorId() == null && mine.length > 0) this.sectorId.set(mine[0].id);
      },
      error: () => this.sectors.set([]),
    });
  }

  loadTasks(): void {
    const s = this.sectorId();
    if (s == null) return;
    this.api.get<KitchenTaskResponse[]>(`/kitchen/tasks?sectorId=${s}`).subscribe({
      next: (t) => this.tasks.set(t),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar tarefas')),
    });
  }

  onSectorChange(value: string): void {
    this.sectorId.set(Number(value));
  }

  start(id: number): void {
    this.api.post<KitchenTaskResponse>(`/kitchen/tasks/${id}/start`).subscribe({
      next: () => this.loadTasks(),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao iniciar preparo')),
    });
  }

  complete(id: number): void {
    this.api.post<KitchenTaskResponse>(`/kitchen/tasks/${id}/complete`).subscribe({
      next: () => this.loadTasks(),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao concluir item')),
    });
  }

  markUnavailable(id: number): void {
    const reason = window.prompt('Motivo da indisponibilidade:');
    if (!reason) return;
    this.api.post<KitchenTaskResponse>(`/kitchen/tasks/${id}/unavailable`, { reason }).subscribe({
      next: () => this.loadTasks(),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao marcar indisponível')),
    });
  }

  canToggle(status: string): boolean {
    return status === 'ENVIADO' || status === 'EM_PREPARO';
  }
}
