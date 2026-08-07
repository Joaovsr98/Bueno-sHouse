import { Component, inject, signal, computed, effect, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { CategoryResponse, ProductResponse, OrderResponse } from '../../core/models';
import { currency } from '../../core/format';
import { OrderPayment } from './order-payment';

interface CartLine {
  productId: number;
  productName: string;
  basePrice: number;
  quantity: number;
  notes: string;
}

const ORDER_STATUS_LABELS: Record<string, string> = {
  RECEBIDO: 'Recebido',
  ENVIADO_PARA_COZINHA: 'Enviado para cozinha',
  EM_PREPARO: 'Em preparo',
  PARCIALMENTE_PRONTO: 'Parcialmente pronto',
  PRONTO: 'Pronto',
  FINALIZADO: 'Finalizado',
  CANCELADO: 'Cancelado',
};

@Component({
  selector: 'app-order',
  imports: [FormsModule, OrderPayment],
  templateUrl: './order.html',
})
export class Order {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly currency = currency;
  readonly orderStatusLabels = ORDER_STATUS_LABELS;
  readonly unitId = this.auth.unitId;

  serviceId = this.route.snapshot.paramMap.get('serviceId')!;
  commandId = this.route.snapshot.paramMap.get('commandId')!;

  categories = signal<CategoryResponse[]>([]);
  products = signal<ProductResponse[]>([]);
  selectedCategoryId = signal<number | null>(null);
  orders = signal<OrderResponse[]>([]);
  cart = signal<CartLine[]>([]);
  error = signal<string | null>(null);
  sending = signal(false);

  readonly commandOrders = computed(() =>
    this.orders().filter((o) => String(o.commandId) === this.commandId),
  );
  readonly cartTotal = computed(() =>
    this.cart().reduce((sum, l) => sum + l.basePrice * l.quantity, 0),
  );

  constructor() {
    effect(() => {
      const u = this.unitId();
      if (u == null) return;
      untracked(() => {
        this.loadCategories();
        this.loadProducts();
        this.loadOrders();
      });
    });
  }

  private loadCategories(): void {
    this.api.get<CategoryResponse[]>(`/categories?unitId=${this.unitId()}`).subscribe({
      next: (c) => this.categories.set(c),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar categorias')),
    });
  }

  loadProducts(): void {
    const cat = this.selectedCategoryId();
    const path = `/products?unitId=${this.unitId()}${cat ? `&categoryId=${cat}` : ''}`;
    this.api.get<ProductResponse[]>(path).subscribe({
      next: (p) => this.products.set(p),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar produtos')),
    });
  }

  loadOrders(): void {
    this.api.get<OrderResponse[]>(`/orders?unitId=${this.unitId()}`).subscribe({
      next: (o) => this.orders.set(o),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar pedidos')),
    });
  }

  selectCategory(id: number | null): void {
    this.selectedCategoryId.set(id);
    this.loadProducts();
  }

  addToCart(product: ProductResponse): void {
    this.cart.update((prev) => {
      const existing = prev.find((l) => l.productId === product.id);
      if (existing) {
        return prev.map((l) =>
          l.productId === product.id ? { ...l, quantity: l.quantity + 1 } : l,
        );
      }
      return [
        ...prev,
        { productId: product.id, productName: product.name, basePrice: product.basePrice, quantity: 1, notes: '' },
      ];
    });
  }

  changeQty(productId: number, delta: number): void {
    this.cart.update((prev) =>
      prev.map((l) =>
        l.productId === productId ? { ...l, quantity: Math.max(1, l.quantity + delta) } : l,
      ),
    );
  }

  setNotes(productId: number, notes: string): void {
    this.cart.update((prev) => prev.map((l) => (l.productId === productId ? { ...l, notes } : l)));
  }

  removeLine(productId: number): void {
    this.cart.update((prev) => prev.filter((l) => l.productId !== productId));
  }

  sendOrder(): void {
    if (this.cart().length === 0) return;
    this.sending.set(true);
    this.api
      .post<OrderResponse>('/orders', {
        unitId: this.unitId(),
        channel: 'SALAO',
        commandId: Number(this.commandId),
        items: this.cart().map((l) => ({
          productId: l.productId,
          quantity: l.quantity,
          notes: l.notes || null,
        })),
      })
      .subscribe({
        next: () => {
          this.cart.set([]);
          this.sending.set(false);
          this.loadOrders();
        },
        error: (e) => {
          this.sending.set(false);
          this.error.set(apiErrorMessage(e, 'Erro ao enviar pedido'));
        },
      });
  }

  sendToKitchen(orderId: number): void {
    this.api.post<OrderResponse>(`/orders/${orderId}/send-to-kitchen`).subscribe({
      next: () => this.loadOrders(),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao enviar para a cozinha')),
    });
  }

  isOrderPayable(status: string): boolean {
    return !['CANCELADO', 'FINALIZADO'].includes(status);
  }

  back(): void {
    this.router.navigate(['/atendimento', this.serviceId]);
  }
}
