import { Component, inject, signal, computed, effect, untracked, OnDestroy } from '@angular/core';
import { forkJoin, map } from 'rxjs';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { KitchenSectorResponse, KitchenTaskResponse } from '../../core/models';
import { elapsedMinutes } from '../../core/format';
import { Icon } from '../../shared/icon';

const STATUS_LABELS: Record<string, string> = {
  PENDENTE: 'Pendente',
  ENVIADO: 'Na fila',
  EM_PREPARO: 'Em preparo',
  PRONTO: 'Pronto',
  ENTREGUE: 'Entregue',
  CANCELADO: 'Cancelado',
};

const STATUS_BADGE: Record<string, string> = {
  ENVIADO: 'badge-yellow',
  EM_PREPARO: 'badge-blue',
  PRONTO: 'badge-green',
};

/** Nomes dos setores como aparecem na tela (o cadastro guarda em caixa alta). */
const SECTOR_LABELS: Record<string, string> = {
  CHAPA: 'Chapa', BEBIDAS: 'Bebidas', SOBREMESAS: 'Sobremesas', EXPEDICAO: 'Expedição',
};

type SectorTask = KitchenTaskResponse & { sectorId: number; sectorName: string };

/**
 * Painel de producao: abre com TODOS os setores juntos (mais antigos primeiro)
 * e so filtra quando a cozinha escolhe um setor.
 */
@Component({
  selector: 'app-kitchen',
  imports: [Icon],
  templateUrl: './kitchen.html',
})
export class Kitchen implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly unitId = this.auth.unitId;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  readonly elapsedMinutes = elapsedMinutes;

  sectors = signal<KitchenSectorResponse[]>([]);
  /** null = todos os setores. */
  sectorId = signal<number | null>(null);
  tasks = signal<SectorTask[]>([]);
  error = signal<string | null>(null);

  readonly visibleTasks = computed(() => {
    const id = this.sectorId();
    return id == null ? this.tasks() : this.tasks().filter((t) => t.sectorId === id);
  });

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor() {
    effect(() => {
      const u = this.unitId();
      if (u == null) return;
      untracked(() => this.loadSectors());
    });
    // Auto-refresh das tarefas a cada 15s.
    this.pollHandle = setInterval(() => this.loadTasks(), 15000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
  }

  sectorLabel(name: string): string {
    return SECTOR_LABELS[name] ?? name;
  }

  countBy(status: string): number {
    return this.visibleTasks().filter((t) => t.status === status).length;
  }

  selectSector(id: number | null): void {
    this.sectorId.set(id);
  }

  private loadSectors(): void {
    this.api.get<KitchenSectorResponse[]>('/kitchen-sectors').subscribe({
      next: (all) => {
        this.sectors.set(all.filter((s) => s.unitId === this.unitId()));
        this.loadTasks();
      },
      error: () => this.sectors.set([]),
    });
  }

  /** Busca a fila de cada setor e junta tudo, do pedido mais antigo para o mais novo. */
  loadTasks(): void {
    const sectors = this.sectors();
    if (sectors.length === 0) return;
    forkJoin(
      sectors.map((s) =>
        this.api
          .get<KitchenTaskResponse[]>(`/kitchen/tasks?sectorId=${s.id}`)
          .pipe(map((list) => list.map((t) => ({ ...t, sectorId: s.id, sectorName: s.name })))),
      ),
    ).subscribe({
      next: (lists) =>
        this.tasks.set(
          lists.flat().sort((a, b) => new Date(a.orderCreatedAt).getTime() - new Date(b.orderCreatedAt).getTime()),
        ),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar tarefas')),
    });
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
