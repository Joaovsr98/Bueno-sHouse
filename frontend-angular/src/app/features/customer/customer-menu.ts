import { Component, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { CartService } from '../../core/cart.service';
import { ToastService } from '../../core/toast.service';
import { CategoryResponse, ProductResponse, UnitResponse } from '../../core/models';
import { currency } from '../../core/format';

interface MenuGroup {
  category: CategoryResponse;
  products: ProductResponse[];
}

/** Cardapio publico (RF-001) com filtro por categoria e detalhe do prato (RF-002). */
@Component({
  selector: 'app-customer-menu',
  imports: [FormsModule],
  templateUrl: './customer-menu.html',
})
export class CustomerMenu {
  private readonly api = inject(ApiService);
  private readonly cart = inject(CartService);
  private readonly toast = inject(ToastService);

  readonly currency = currency;
  groups = signal<MenuGroup[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  categoryId = signal<number | null>(null);

  readonly visibleGroups = computed(() => {
    const id = this.categoryId();
    return id == null ? this.groups() : this.groups().filter((g) => g.category.id === id);
  });

  detail = signal<ProductResponse | null>(null);
  detailQty = 1;
  detailNotes = '';

  constructor() {
    this.load();
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const units = await firstValueFrom(this.api.get<UnitResponse[]>('/units'));
      const unitId = units[0]?.id ?? 1;
      const [categories, products] = await Promise.all([
        firstValueFrom(this.api.get<CategoryResponse[]>(`/categories?unitId=${unitId}`)),
        firstValueFrom(this.api.get<ProductResponse[]>(`/products?unitId=${unitId}`)),
      ]);
      const groups = categories
        .map((category) => ({
          category,
          products: products.filter((p) => p.categoryId === category.id && p.available),
        }))
        .filter((g) => g.products.length > 0);
      this.groups.set(groups);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erro ao carregar o cardápio'));
    } finally {
      this.loading.set(false);
    }
  }

  openDetail(product: ProductResponse): void {
    this.detail.set(product);
    this.detailQty = 1;
    this.detailNotes = '';
  }

  closeDetail(): void {
    this.detail.set(null);
  }

  changeDetailQty(delta: number): void {
    this.detailQty = Math.max(1, this.detailQty + delta);
  }

  addFromDetail(): void {
    const product = this.detail();
    if (!product) return;
    this.cart.add(product, this.detailQty, this.detailNotes.trim());
    this.toast.success(`${this.detailQty}x ${product.name} adicionado ao carrinho`);
    this.closeDetail();
  }

  quickAdd(product: ProductResponse): void {
    this.cart.add(product);
    this.toast.success(`${product.name} adicionado ao carrinho`);
  }
}
