import { Injectable, signal, computed } from '@angular/core';
import { ProductResponse } from './models';

export interface CartLine {
  productId: number;
  productName: string;
  basePrice: number;
  quantity: number;
}

/** Carrinho do cliente, compartilhado entre o cardapio e o checkout. */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly _lines = signal<CartLine[]>([]);
  readonly lines = this._lines.asReadonly();
  readonly count = computed(() => this._lines().reduce((s, l) => s + l.quantity, 0));
  readonly subtotal = computed(() => this._lines().reduce((s, l) => s + l.basePrice * l.quantity, 0));

  add(product: ProductResponse): void {
    this._lines.update((prev) => {
      const existing = prev.find((l) => l.productId === product.id);
      if (existing) {
        return prev.map((l) => (l.productId === product.id ? { ...l, quantity: l.quantity + 1 } : l));
      }
      return [...prev, { productId: product.id, productName: product.name, basePrice: product.basePrice, quantity: 1 }];
    });
  }

  changeQty(productId: number, delta: number): void {
    this._lines.update((prev) =>
      prev
        .map((l) => (l.productId === productId ? { ...l, quantity: l.quantity + delta } : l))
        .filter((l) => l.quantity > 0),
    );
  }

  remove(productId: number): void {
    this._lines.update((prev) => prev.filter((l) => l.productId !== productId));
  }

  clear(): void {
    this._lines.set([]);
  }
}
