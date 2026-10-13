import { Component, computed, inject, signal, effect, untracked } from '@angular/core';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { currency } from '../../core/format';
import { ChartComponent } from './chart';

interface DailyRevenue { day: string; revenue: number; }
interface TopProduct { productName: string; totalSold: number; }
interface LowStock { id: number; name: string; currentQuantity: number; minimumQuantity: number; unitOfMeasure: string; }

@Component({
  selector: 'app-dashboard',
  imports: [ChartComponent],
  template: `
    <div class="page">
      <div class="mb-6">
        <h1 class="page-title">Dashboard</h1>
        <p class="page-sub">Faturamento, ticket médio, food cost e alertas de estoque da unidade.</p>
      </div>

      @if (error()) {
        <div class="alert-error mb-4"><span>{{ error() }}</span><button (click)="error.set(null)" class="text-red-400">×</button></div>
      }

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <div class="glass rounded-2xl p-4">
          <p class="field-label">Faturamento no período</p>
          <p class="kpi-value brand-text">{{ currency(totalRevenue()) }}</p>
        </div>
        <div class="glass rounded-2xl p-4">
          <p class="field-label">Ticket médio</p>
          <p class="kpi-value">{{ avgTicket() != null ? currency(avgTicket()!) : '—' }}</p>
        </div>
        <div class="glass rounded-2xl p-4">
          <p class="field-label">Pedidos</p>
          <p class="kpi-value">{{ orderCount() }}</p>
        </div>
        <div class="glass rounded-2xl p-4">
          <p class="field-label">Food cost médio</p>
          <p class="kpi-value">{{ foodCost() != null ? foodCostText() : '—' }}</p>
          @if (foodCost() != null) {
            <span class="badge mt-1" [class]="foodCostClass()">{{ foodCostLevel() }}</span>
          }
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
        <section class="glass rounded-2xl p-5">
          <h2 class="section-title">Faturamento por dia</h2>
          @if (revenue().length === 0) {
            <p class="empty">Sem vendas no período.</p>
          } @else {
            <app-chart type="line" label="Faturamento" [labels]="revenueLabels()" [values]="revenueValues()" [formatValue]="money" />
          }
        </section>
        <section class="glass rounded-2xl p-5">
          <h2 class="section-title">Top 5 produtos</h2>
          @if (top().length === 0) {
            <p class="empty">Sem vendas no período.</p>
          } @else {
            <app-chart type="bar" label="Vendidos" [labels]="topLabels()" [values]="topValues()" />
          }
        </section>
      </div>

      <section class="glass rounded-2xl overflow-hidden">
        <div class="px-5 pt-5"><h2 class="section-title">Estoque abaixo do mínimo</h2></div>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>Item</th><th class="num">Atual</th><th class="num">Mínimo</th><th>Situação</th></tr></thead>
            <tbody>
              @for (i of low(); track i.id) {
                <tr>
                  <td>{{ i.name }}</td>
                  <td class="num">{{ i.currentQuantity }} {{ i.unitOfMeasure }}</td>
                  <td class="num">{{ i.minimumQuantity }} {{ i.unitOfMeasure }}</td>
                  <td><span class="badge" [class]="i.currentQuantity <= 0 ? 'badge-red' : 'badge-yellow'">{{ i.currentQuantity <= 0 ? 'Zerado' : 'Baixo' }}</span></td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="empty">Nenhum item abaixo do mínimo.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
})
export class Dashboard {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly currency = currency;
  readonly money = (v: number): string => currency(v);

  revenue = signal<DailyRevenue[]>([]);
  top = signal<TopProduct[]>([]);
  low = signal<LowStock[]>([]);
  avgTicket = signal<number | null>(null);
  orderCount = signal(0);
  foodCost = signal<number | null>(null);
  error = signal<string | null>(null);

  revenueLabels = computed(() => this.revenue().map((r) => r.day));
  revenueValues = computed(() => this.revenue().map((r) => r.revenue));
  topLabels = computed(() => this.top().map((p) => p.productName));
  topValues = computed(() => this.top().map((p) => p.totalSold));
  totalRevenue = computed(() => this.revenue().reduce((sum, r) => sum + Number(r.revenue), 0));

  foodCostText = computed(() => `${Number(this.foodCost() ?? 0).toFixed(1).replace('.', ',')}%`);
  // Mesmos limites do backend (RN02): <=30 saudável, 31-35 atenção, >35 alto.
  foodCostClass = computed(() => {
    const f = Number(this.foodCost() ?? 0);
    return f <= 30 ? 'badge-green' : f <= 35 ? 'badge-yellow' : 'badge-red';
  });
  foodCostLevel = computed(() => {
    const f = Number(this.foodCost() ?? 0);
    return f <= 30 ? 'Saudável' : f <= 35 ? 'Atenção' : 'Alto';
  });

  constructor() {
    effect(() => {
      const u = this.auth.unitId();
      if (u == null) return;
      untracked(() => this.load(u));
    });
  }

  private load(u: number): void {
    const fail = (e: unknown) => this.error.set(apiErrorMessage(e, 'Erro ao carregar relatórios'));
    this.api.get<DailyRevenue[]>(`/reports/daily-revenue?unitId=${u}`).subscribe({ next: (r) => this.revenue.set(r), error: fail });
    this.api.get<TopProduct[]>(`/reports/top-products?unitId=${u}&limit=5`).subscribe({ next: (r) => this.top.set(r), error: fail });
    this.api.get<LowStock[]>(`/reports/low-stock?unitId=${u}`).subscribe({ next: (r) => this.low.set(r), error: fail });
    this.api.get<{ averageTicket: number; orderCount: number }>(`/reports/average-ticket?unitId=${u}`).subscribe({
      next: (r) => {
        this.avgTicket.set(r.averageTicket);
        this.orderCount.set(r.orderCount);
      },
      error: fail,
    });
    this.api.get<number>(`/reports/average-food-cost?unitId=${u}`).subscribe({ next: (r) => this.foodCost.set(r), error: fail });
  }
}
