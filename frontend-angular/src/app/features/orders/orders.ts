import { Component, inject, signal, effect, untracked, OnDestroy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { DeliveryResponse, OrderResponse } from '../../core/models';
import { currency } from '../../core/format';
import { CHANNEL_LABELS, ORDER_STATUS_LABELS, orderStatusLabel } from '../../core/order-status';
import { ToastService } from '../../core/toast.service';

const ACTIVE = ['RECEBIDO', 'ENVIADO_PARA_COZINHA', 'EM_PREPARO', 'PARCIALMENTE_PRONTO', 'PRONTO', 'AGUARDANDO_ENTREGADOR', 'SAIU_PARA_ENTREGA'];

/** Opcoes do filtro de status: "ativos" agrupa o que ainda esta em andamento. */
const STATUS_FILTERS = [
  { value: 'ATIVOS', label: 'Em andamento', statuses: ACTIVE },
  ...Object.keys(ORDER_STATUS_LABELS).map((s) => ({ value: s, label: ORDER_STATUS_LABELS[s], statuses: [s] })),
  { value: 'TODOS', label: 'Todos', statuses: Object.keys(ORDER_STATUS_LABELS) },
];

const PAGE_SIZE = 10;

/**
 * Painel de pedidos da equipe (RF-015, RF-019, RF-020): lista paginada com
 * filtros de status, canal e periodo; detalhe do pedido; confirmacao
 * (RECEBIDO -> CONFIRMADO, que baixa o estoque) e cancelamento com motivo.
 */
@Component({
  selector: 'app-orders',
  imports: [FormsModule, DatePipe],
  templateUrl: './orders.html',
})
export class Orders implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly unitId = this.auth.unitId;
  readonly currency = currency;
  readonly statusLabel = orderStatusLabel;
  readonly channelLabels = CHANNEL_LABELS;
  readonly channels = Object.keys(CHANNEL_LABELS);
  readonly statusFilters = STATUS_FILTERS;

  statusFilter = 'ATIVOS';
  channel = '';
  from = '';
  to = '';

  orders = signal<OrderResponse[]>([]);
  total = signal(0);
  page = signal(0);
  loading = signal(false);
  selected = signal<OrderResponse | null>(null);
  delivery = signal<DeliveryResponse | null>(null);
  busy = signal(false);

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor() {
    effect(() => {
      const u = this.unitId();
      if (u == null) return;
      untracked(() => this.load());
    });
    // Pedidos novos aparecem sozinhos no painel (RF-015)
    this.pollHandle = setInterval(() => this.load(true), 20000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
  }

  pages(): number {
    return Math.max(1, Math.ceil(this.total() / PAGE_SIZE));
  }

  applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  goTo(page: number): void {
    this.page.set(page);
    this.load();
  }

  load(silent = false): void {
    if (this.unitId() == null) return;
    const statuses = STATUS_FILTERS.find((f) => f.value === this.statusFilter)?.statuses ?? ACTIVE;
    let params = new HttpParams()
      .set('unitId', String(this.unitId()))
      .set('page', String(this.page()))
      .set('size', String(PAGE_SIZE));
    statuses.forEach((s) => (params = params.append('status', s)));
    if (this.channel) params = params.set('channel', this.channel);
    if (this.from) params = params.set('from', this.from);
    if (this.to) params = params.set('to', this.to);

    if (!silent) this.loading.set(true);
    this.http.get<OrderResponse[]>('/api/orders', { params, observe: 'response' }).subscribe({
      next: (res) => {
        this.orders.set(res.body ?? []);
        this.total.set(Number(res.headers.get('X-Total-Count') ?? res.body?.length ?? 0));
        const sel = this.selected();
        if (sel) this.selected.set((res.body ?? []).find((o) => o.id === sel.id) ?? sel);
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        if (!silent) this.toast.error(apiErrorMessage(e, 'Erro ao carregar pedidos'));
      },
    });
  }

  open(order: OrderResponse): void {
    this.selected.set(order);
    this.delivery.set(null);
    if (order.channel === 'DELIVERY') {
      this.api.get<DeliveryResponse>(`/deliveries/by-order/${order.id}`).subscribe({
        next: (d) => this.delivery.set(d),
        error: () => this.delivery.set(null),
      });
    }
  }

  close(): void {
    this.selected.set(null);
  }

  confirm(order: OrderResponse): void {
    this.run(this.api.post<OrderResponse>(`/orders/${order.id}/send-to-kitchen`), `Pedido #${order.orderNumber} confirmado`);
  }

  finish(order: OrderResponse): void {
    this.run(
      this.api.post<OrderResponse>(`/orders/${order.id}/transitions`, { status: 'FINALIZADO' }),
      `Pedido #${order.orderNumber} finalizado`,
    );
  }

  cancel(order: OrderResponse): void {
    const reason = window.prompt(`Motivo do cancelamento do pedido #${order.orderNumber}:`);
    if (!reason?.trim()) return;
    this.run(
      this.api.post<OrderResponse>(`/orders/${order.id}/transitions`, { status: 'CANCELADO', reason }),
      `Pedido #${order.orderNumber} cancelado`,
    );
  }

  canConfirm(o: OrderResponse): boolean {
    return o.status === 'RECEBIDO';
  }

  canFinish(o: OrderResponse): boolean {
    return o.channel !== 'DELIVERY' && o.status === 'PRONTO';
  }

  canCancel(o: OrderResponse): boolean {
    return o.status === 'RECEBIDO' || o.status === 'ENVIADO_PARA_COZINHA';
  }

  private run(req: ReturnType<ApiService['post']>, success: string): void {
    this.busy.set(true);
    req.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.toast.success(success);
        if (this.selected()) this.selected.set(updated as OrderResponse);
        this.load(true);
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(apiErrorMessage(e, 'Nao foi possivel concluir a acao'));
      },
    });
  }
}
