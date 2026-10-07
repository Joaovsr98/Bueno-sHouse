import { Component, inject, signal, effect, untracked } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ProductResponse } from '../../core/models';

interface StockItem { id: number; name: string; unitOfMeasure: string; }
interface Recipe {
  costPerServing: number | null;
  foodCostPercent: number | null;
  foodCostLevel: string | null;
  items: { inventoryItemId: number; quantity: number; correctionFactor: number }[];
  yieldQuantity: number;
}

@Component({
  selector: 'app-recipes',
  host: { class: 'page-basic' },
  imports: [ReactiveFormsModule],
  template: `
    <h1>Fichas técnicas</h1>
    @if (error()) { <p>{{ error() }}</p> }
    @if (message()) { <p>{{ message() }}</p> }

    <label>Prato:
      <select [formControl]="productId" (change)="loadRecipe()">
        <option value="">-- escolha --</option>
        @for (p of products(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
      </select>
    </label>

    @if (productId.value) {
      <form [formGroup]="form" (ngSubmit)="save()">
        <p><label>Rendimento (porções): <input type="number" step="any" formControlName="yieldQuantity" /></label></p>

        <table border="1" formArrayName="items">
          <tr><th>Ingrediente</th><th>Quantidade</th><th>Fator de correção (≥ 1)</th><th></th></tr>
          @for (row of items.controls; track row; let i = $index) {
            <tr [formGroupName]="i">
              <td>
                <select formControlName="inventoryItemId">
                  @for (s of stock(); track s.id) { <option [ngValue]="s.id">{{ s.name }} ({{ s.unitOfMeasure }})</option> }
                </select>
              </td>
              <td><input type="number" step="any" formControlName="quantity" /></td>
              <td><input type="number" step="any" formControlName="correctionFactor" /></td>
              <td><button type="button" (click)="removeItem(i)">Remover</button></td>
            </tr>
          }
        </table>
        <button type="button" (click)="addItem()">+ Ingrediente</button>
        <button type="submit" [disabled]="form.invalid || items.length === 0">Salvar ficha</button>
      </form>

      @if (recipe(); as r) {
        <p>Custo por porção: {{ r.costPerServing }}</p>
        <p>Food cost: {{ r.foodCostPercent }}% ({{ r.foodCostLevel }})</p>
      }
    }
  `,
})
export class Recipes {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  products = signal<ProductResponse[]>([]);
  stock = signal<StockItem[]>([]);
  recipe = signal<Recipe | null>(null);
  error = signal<string | null>(null);
  message = signal<string | null>(null);

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
    this.api.post<Recipe>('/inventory/recipes', body).subscribe({
      next: (r) => {
        this.recipe.set(r);
        this.message.set('Ficha salva.');
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao salvar ficha')),
    });
  }
}
