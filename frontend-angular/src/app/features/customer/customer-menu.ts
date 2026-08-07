import { Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { CartService } from '../../core/cart.service';
import { CategoryResponse, ProductResponse, UnitResponse } from '../../core/models';
import { currency } from '../../core/format';

interface MenuGroup {
  category: CategoryResponse;
  products: ProductResponse[];
}

@Component({
  selector: 'app-customer-menu',
  imports: [],
  templateUrl: './customer-menu.html',
})
export class CustomerMenu {
  private readonly api = inject(ApiService);
  private readonly cart = inject(CartService);

  readonly currency = currency;
  groups = signal<MenuGroup[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  added = signal<number | null>(null);

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

  add(product: ProductResponse): void {
    this.cart.add(product);
    this.added.set(product.id);
    setTimeout(() => {
      if (this.added() === product.id) this.added.set(null);
    }, 1000);
  }
}
