import { Component, computed, inject, signal, effect, untracked } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ProductResponse } from '../../core/models';
import { currency } from '../../core/format';

interface StockItem { id: number; name: string; unitOfMeasure: string; }
interface Recipe {
  id?: number;
  costPerServing: number | null;
  foodCostPercent: number | null;
  foodCostLevel: string | null;
  items: { inventoryItemId: number; quantity: number; correctionFactor: number }[];
  yieldQuantity: number;
}

@Component({
  selector: 'app-recipes',
  imports: [ReactiveFormsModule],
  template: `
    <div class="page">
      <div class="mb-6">
        <h1 class="page-title">Fichas técnicas</h1>
        <p class="page-sub">Ingredientes, fator de correção e custo de cada prato. O prato só pode ser ativado no cardápio depois da ficha (RN01).</p>
      </div>

      @if (error()) {
        <div class="alert-error mb-4"><span>{{ error() }}</span><button (click)="error.set(null)" class="text-red-400">×</button></div>
      }
      @if (message()) { <div class="alert-ok mb-4">{{ message() }}</div> }

      <section class="glass rounded-2xl p-5 mb-6">
        <label class="field-label" for="r-prod">Prato</label>
        <select id="r-prod" class="field md:max-w-md" [formControl]="productId" (change)="loadRecipe()">
          <option value="">Escolha um prato…</option>
          @for (p of products(); track p.id) {
            <option [value]="p.id">{{ p.name }}{{ p.available ? '' : ' (inativo)' }}</option>
          }
        </select>
      </section>

      @if (product(); as prod) {
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <div class="glass rounded-2xl p-4">
            <p class="field-label">Preço de venda</p>
            <p class="kpi-value">{{ currency(prod.basePrice) }}</p>
          </div>
          <div class="glass rounded-2xl p-4">
            <p class="field-label">Custo por porção</p>
            <p class="kpi-value">{{ costLabel() }}</p>
          </div>
          <div class="glass rounded-2xl p-4">
            <p class="field-label">Food cost</p>
            <p class="kpi-value">{{ foodCostLabel() }}</p>
            @if (recipe()?.foodCostLevel; as level) {
              <span class="badge mt-1" [class]="levelClass(level)">{{ levelLabel(level) }}</span>
            }
          </div>
          <div class="glass rounded-2xl p-4">
            <p class="field-label">Situação no cardápio</p>
            <span class="badge" [class]="prod.available ? 'badge-green' : 'badge-gray'">{{ prod.available ? 'Ativo' : 'Inativo' }}</span>
            <div class="mt-2">
              @if (!prod.available) {
                <button class="btn-ghost" [disabled]="!recipe()?.id" (click)="setAvailability(true)">Ativar prato</button>
                @if (!recipe()?.id) { <p class="text-[11px] text-neutral-500 mt-1">Salve a ficha para poder ativar.</p> }
              } @else {
                <button class="btn-ghost btn-danger" (click)="setAvailability(false)">Desativar</button>
              }
            </div>
          </div>
        </div>

        <form [formGroup]="form" (ngSubmit)="save()" class="glass rounded-2xl p-5">
          <div class="flex flex-wrap items-end justify-between gap-3 mb-4">
            <h2 class="section-title !mb-0">Ingredientes</h2>
            <div class="w-44">
              <label class="field-label" for="r-yield">Rendimento (porções)</label>
              <input id="r-yield" class="field" type="number" step="any" formControlName="yieldQuantity" />
            </div>
          </div>

          <div class="table-wrap" formArrayName="items">
            <table class="data-table">
              <thead>
                <tr><th>Ingrediente</th><th class="w-32">Quantidade</th><th class="w-44">Fator de correção (≥ 1)</th><th class="w-10"></th></tr>
              </thead>
              <tbody>
                @for (row of items.controls; track row; let i = $index) {
                  <tr [formGroupName]="i">
                    <td>
                      <select class="field" formControlName="inventoryItemId">
                        <option [ngValue]="null">Escolha…</option>
                        @for (s of stock(); track s.id) { <option [ngValue]="s.id">{{ s.name }} ({{ s.unitOfMeasure }})</option> }
                      </select>
                    </td>
                    <td><input class="field" type="number" step="any" formControlName="quantity" /></td>
                    <td><input class="field" type="number" step="any" formControlName="correctionFactor" /></td>
                    <td><button type="button" class="btn-ghost btn-danger" title="Remover" (click)="removeItem(i)">×</button></td>
                  </tr>
                } @empty {
                  <tr><td colspan="4" class="empty">Nenhum ingrediente. Adicione o primeiro abaixo.</td></tr>
                }
              </tbody>
            </table>
          </div>

          <div class="flex justify-between mt-4">
            <button type="button" class="btn-ghost" (click)="addItem()">+ Ingrediente</button>
            <button type="submit" class="btn-brand rounded-xl px-5 py-2 text-sm" [disabled]="form.invalid || items.length === 0 || saving()">
              {{ saving() ? 'Salvando…' : 'Salvar ficha' }}
            </button>
          </div>
        </form>
      }
    </div>
  `,
})
export class Recipes {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly currency = currency;
  products = signal<ProductResponse[]>([]);
  stock = signal<StockItem[]>([]);
  recipe = signal<Recipe | null>(null);
  error = signal<string | null>(null);
  message = signal<string | null>(null);
  saving = signal(false);

  private readonly selectedId = signal<number | null>(null);
  product = computed(() => this.products().find((p) => p.id === this.selectedId()) ?? null);
  costLabel = computed(() => {
    const c = this.recipe()?.costPerServing;
    return c != null ? currency(c) : '—';
  });
  foodCostLabel = computed(() => {
    const f = this.recipe()?.foodCostPercent;
    return f != null ? `${Number(f).toFixed(1).replace('.', ',')}%` : '—';
  });

  productId = this.fb.control('');
  form = this.fb.group({
    yieldQuantity: [1, [Validators.required, Validators.min(0.0001)]],
    items: this.fb.array([]),
  });

  get items(): FormArray {
    return this.form.controls.items as FormArray;
  }

  constructor() {
    effect(() => {
      const u = this.auth.unitId();
      if (u == null) return;
      untracked(() => {
        this.api.get<ProductResponse[]>(`/products?unitId=${u}`).subscribe({
          next: (p) => this.products.set(p),
          error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar pratos')),
        });
        this.api.get<StockItem[]>(`/inventory/items?unitId=${u}`).subscribe({
          next: (s) => this.stock.set(s),
          error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar estoque')),
        });
      });
    });
  }

  private newItem(inventoryItemId: number | null = null, quantity = 1, correctionFactor = 1) {
    return this.fb.group({
      inventoryItemId: [inventoryItemId, Validators.required],
      quantity: [quantity, [Validators.required, Validators.min(0.0001)]],
      correctionFactor: [correctionFactor, [Validators.required, Validators.min(1)]],
    });
  }

  addItem(): void {
    this.items.push(this.newItem());
  }

  removeItem(i: number): void {
    this.items.removeAt(i);
  }

  loadRecipe(): void {
    this.error.set(null);
    this.message.set(null);
    this.recipe.set(null);
    this.items.clear();
    const id = this.productId.value;
    this.selectedId.set(id ? Number(id) : null);
    if (!id) return;
    this.api.get<Recipe>(`/inventory/recipes/${id}`).subscribe({
      next: (r) => {
        this.recipe.set(r);
        this.form.controls.yieldQuantity.setValue(r.yieldQuantity);
        r.items.forEach((it) => this.items.push(this.newItem(it.inventoryItemId, it.quantity, it.correctionFactor)));
      },
      // Sem ficha ainda: formulario vazio para cadastrar.
      error: () => this.addItem(),
    });
  }

  save(): void {
    this.error.set(null);
    this.message.set(null);
    const body = {
      productId: Number(this.productId.value),
      yieldQuantity: this.form.value.yieldQuantity,
      items: this.items.value,
    };
    this.saving.set(true);
    this.api.post<Recipe>('/inventory/recipes', body).subscribe({
      next: (r) => {
        this.saving.set(false);
        this.recipe.set(r);
        this.message.set('Ficha salva.');
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(e, 'Erro ao salvar ficha'));
      },
    });
  }

  setAvailability(available: boolean): void {
    const prod = this.product();
    if (!prod) return;
    this.error.set(null);
    this.message.set(null);
    this.api.patch<ProductResponse>(`/products/${prod.id}/availability`, { available }).subscribe({
      next: (updated) => {
        this.products.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
        this.message.set(available ? 'Prato ativado no cardápio.' : 'Prato desativado.');
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao alterar disponibilidade')),
    });
  }

  levelClass(level: string): string {
    return level === 'GREEN' ? 'badge-green' : level === 'YELLOW' ? 'badge-yellow' : 'badge-red';
  }

  levelLabel(level: string): string {
    return level === 'GREEN' ? 'Saudável' : level === 'YELLOW' ? 'Atenção' : 'Alto';
  }
}
