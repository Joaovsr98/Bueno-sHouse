import { Component, inject, signal, computed, effect, untracked } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { currency } from '../../core/format';

interface InventoryItem {
  id: number;
  unitId: number;
  name: string;
  unitOfMeasure: string;
  currentQuantity: number;
  minimumQuantity: number;
  costPerUnit: number;
  belowMinimum: boolean;
}

interface StockMovement {
  id: number;
  type: string;
  quantity: number;
  reason: string | null;
  createdAt: string;
}

interface ItemForm {
  id: number | null;
  name: string;
  unitOfMeasure: string;
  minimumQuantity: string;
  costPerUnit: string;
}

const UNITS = ['UN', 'KG', 'G', 'L', 'ML'];

/** RF-030: a saida manual sempre tem um motivo, escolhido de uma lista fixa. */
const LOSS_REASONS = ['Vencimento', 'Quebra', 'Desperdício', 'Contaminação', 'Ajuste de inventário'];

const MOVEMENT_LABELS: Record<string, string> = {
  ENTRADA_COMPRA: 'Entrada',
  AJUSTE_PERDA: 'Saída (perda)',
  SAIDA_PRODUCAO: 'Saída (pedido)',
  ESTORNO_CANCELAMENTO: 'Estorno (cancelamento)',
};

/** Controle de estoque (RF-027 a RF-033). */
@Component({
  selector: 'app-inventory',
  imports: [FormsModule, DatePipe, DecimalPipe],
  templateUrl: './inventory.html',
})
export class Inventory {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly unitId = this.auth.unitId;
  readonly currency = currency;
  readonly units = UNITS;
  readonly lossReasons = LOSS_REASONS;
  readonly movementLabels = MOVEMENT_LABELS;

  items = signal<InventoryItem[]>([]);
  loading = signal(true);
  search = signal('');
  readonly alerts = computed(() => this.items().filter((i) => i.belowMinimum));
  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    return q ? this.items().filter((i) => i.name.toLowerCase().includes(q)) : this.items();
  });

  form = signal<ItemForm | null>(null);
  selected = signal<InventoryItem | null>(null);
  movements = signal<StockMovement[]>([]);
  movementType: 'ENTRADA_COMPRA' | 'AJUSTE_PERDA' = 'ENTRADA_COMPRA';
  movementQty = '';
  movementReason = '';
  saving = signal(false);

  constructor() {
    effect(() => {
      const u = this.unitId();
      if (u == null) return;
      untracked(() => this.load());
    });
  }

  load(): void {
    this.loading.set(true);
    this.api.get<InventoryItem[]>(`/inventory/items?unitId=${this.unitId()}`).subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
        const sel = this.selected();
        if (sel) this.selected.set(list.find((i) => i.id === sel.id) ?? null);
      },
      error: (e) => {
        this.loading.set(false);
        this.toast.error(apiErrorMessage(e, 'Erro ao carregar o estoque'));
      },
    });
  }

  newItem(): void {
    this.form.set({ id: null, name: '', unitOfMeasure: 'UN', minimumQuantity: '0', costPerUnit: '0' });
  }

  editItem(item: InventoryItem): void {
    this.form.set({
      id: item.id,
      name: item.name,
      unitOfMeasure: item.unitOfMeasure,
      minimumQuantity: String(item.minimumQuantity),
      costPerUnit: String(item.costPerUnit ?? 0),
    });
  }

  saveItem(): void {
    const f = this.form();
    if (!f || !f.name.trim()) return;
    const body = {
      unitId: this.unitId(),
      name: f.name.trim(),
      unitOfMeasure: f.unitOfMeasure,
      minimumQuantity: Number(f.minimumQuantity || 0),
      costPerUnit: Number(f.costPerUnit || 0),
    };
    const req = f.id
      ? this.api.put<InventoryItem>(`/inventory/items/${f.id}`, body)
      : this.api.post<InventoryItem>('/inventory/items', body);
    this.saving.set(true);
    req.subscribe({
      next: () => {
        this.saving.set(false);
        this.form.set(null);
        this.toast.success(f.id ? 'Ingrediente atualizado' : 'Ingrediente cadastrado');
        this.load();
      },
      error: (e) => {
        this.saving.set(false);
        this.toast.error(apiErrorMessage(e, 'Erro ao salvar o ingrediente'));
      },
    });
  }

  deactivate(item: InventoryItem): void {
    if (!confirm(`Desativar "${item.name}"? O histórico continua guardado.`)) return;
    this.api.delete<void>(`/inventory/items/${item.id}`).subscribe({
      next: () => {
        if (this.selected()?.id === item.id) this.selected.set(null);
        this.toast.success('Ingrediente desativado');
        this.load();
      },
      error: (e) => this.toast.error(apiErrorMessage(e, 'Erro ao desativar')),
    });
  }

  select(item: InventoryItem): void {
    this.selected.set(item);
    this.movementType = 'ENTRADA_COMPRA';
    this.movementQty = '';
    this.movementReason = '';
    this.loadMovements(item.id);
  }

  private loadMovements(id: number): void {
    this.api.get<StockMovement[]>(`/inventory/items/${id}/movements`).subscribe({
      next: (m) => this.movements.set(m),
      error: () => this.movements.set([]),
    });
  }

  registerMovement(): void {
    const item = this.selected();
    if (!item || !this.movementQty) return;
    if (this.movementType === 'AJUSTE_PERDA' && !this.movementReason) {
      this.toast.error('Escolha o motivo da saída');
      return;
    }
    this.saving.set(true);
    this.api
      .post(`/inventory/items/${item.id}/movements`, {
        type: this.movementType,
        quantity: Number(this.movementQty),
        reason: this.movementType === 'AJUSTE_PERDA' ? this.movementReason : 'Entrada manual',
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.movementQty = '';
          this.movementReason = '';
          this.toast.success('Movimentação registrada');
          this.load();
          this.loadMovements(item.id);
        },
        error: (e) => {
          this.saving.set(false);
          this.toast.error(apiErrorMessage(e, 'Erro ao registrar a movimentação'));
        },
      });
  }

  isOut(type: string): boolean {
    return type === 'AJUSTE_PERDA' || type === 'SAIDA_PRODUCAO';
  }
}
