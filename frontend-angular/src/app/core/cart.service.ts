import { Injectable, signal, computed, effect } from '@angular/core';
import { ProductResponse } from './models';

export interface CartLine {
  productId: number;
  productName: string;
  basePrice: number;
  quantity: number;
  /** Observacao do cliente para o item (RF-003), ex.: "sem cebola". */
  notes: string;
}

const CART_KEY = 'bueno.cart';

function loadLines(): CartLine[] {
  try {
    const raw = JSON.parse(localStorage.getItem(CART_KEY) ?? '[]');
    if (!Array.isArray(raw)) return [];
    return raw.filter(
      (l): l is CartLine =>
        typeof l?.productId === 'number' && typeof l?.productName === 'string' &&
        typeof l?.basePrice === 'number' && typeof l?.quantity === 'number' && l.quantity > 0,
    ).map((l) => ({ ...l, notes: typeof l.notes === 'string' ? l.notes : '' }));
  } catch {
    return [];
  }
}

/**
 * Carrinho do cliente, compartilhado entre o cardapio e o checkout.
 * Persistido em localStorage para sobreviver a recarregar a pagina.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly _lines = signal<CartLine[]>(loadLines());
  readonly lines = this._lines.asReadonly();
  readonly count = computed(() => this._lines().reduce((s, l) => s + l.quantity, 0));
  readonly subtotal = computed(() => this._lines().reduce((s, l) => s + l.basePrice * l.quantity, 0));

  constructor() {
    effect(() => {
      const lines = this._lines();
      try {
        localStorage.setItem(CART_KEY, JSON.stringify(lines));
      } catch {
        // armazenamento indisponivel (modo privado/cheio): o carrinho segue so em memoria
      }
    });
  }

  add(product: ProductResponse, quantity = 1, notes = ''): void {
    this._lines.update((prev) => {
      const existing = prev.find((l) => l.productId === product.id);
      if (existing) {
        return prev.map((l) =>
          l.productId === product.id
            ? { ...l, quantity: l.quantity + quantity, notes: notes || l.notes }
            : l,
        );
      }
      return [
        ...prev,
        { productId: product.id, productName: product.name, basePrice: product.basePrice, quantity, notes },
      ];
    });
  }

  setNotes(productId: number, notes: string): void {
    this._lines.update((prev) => prev.map((l) => (l.productId === productId ? { ...l, notes } : l)));
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
