import { Component, computed, inject, signal, effect, untracked } from '@angular/core';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { currency } from '../../core/format';
import { ChartComponent } from './chart';
import { CountUp } from '../../shared/count-up';
import { Ring } from '../../shared/ring';

interface DailyRevenue { day: string; revenue: number; }
interface TopProduct { productName: string; totalSold: number; }
interface LowStock { id: number; name: string; currentQuantity: number; minimumQuantity: number; unitOfMeasure: string; }

@Component({
  selector: 'app-dashboard',
  imports: [ChartComponent, CountUp, Ring],
  template: `
    <div class="page">
      <header class="mb-8 reveal">
        <p class="eyebrow">Visão geral · Unidade</p>
        <h1 class="page-title mt-3">Dashboard</h1>
        <p class="page-sub mt-2">Faturamento, ticket médio, food cost e alertas de estoque.</p>
      </header>

      @if (error()) {
        <div class="alert-error mb-4"><span>{{ error() }}</span><button (click)="error.set(null)" class="text-red-400">×</button></div>
      }

      <!-- Faixa de indicadores (numeros grandes + legenda mono) -->
      <section class="grid grid-cols-2 lg:grid-cols-4 gap-x-8 gap-y-6 mb-8 reveal reveal-1">
        <div class="border-t border-white/10 pt-4">
          <p class="kpi-value text-ember"><app-count-up [value]="totalRevenue()" [format]="money" /></p>
          <p class="kpi-label mt-2">Faturamento no período</p>
        </div>
        <div class="border-t border-white/10 pt-4">
          <p class="kpi-value">@if (avgTicket() != null) { <app-count-up [value]="avgTicket()!" [format]="money" /> } @else { - }</p>
          <p class="kpi-label mt-2">Ticket médio</p>
        </div>
        <div class="border-t border-white/10 pt-4">
          <p class="kpi-value"><app-count-up [value]="orderCount()" /></p>
          <p class="kpi-label mt-2">Pedidos</p>
        </div>
        <div class="border-t border-white/10 pt-4 flex items-center gap-4">
          <app-ring [value]="foodCost()" [size]="64" />
          <div>
            <p class="kpi-label">Food cost médio</p>
            @if (foodCost() != null) {
              <span class="badge mt-2" [class]="foodCostClass()">{{ foodCostLevel() }}</span>
            }
          </div>
        </div>
      </section>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6 reveal reveal-2">
        <section class="glass accent-card rounded-2xl p-6">
          <h2 class="section-title">Faturamento por dia</h2>
          @if (revenue().length === 0) {
            <p class="empty">Sem vendas no período.</p>
          } @else {
            <app-chart type="line" label="Faturamento" [labels]="revenueLabels()" [values]="revenueValues()" [formatValue]="money" />
          }
        </section>
        <section class="glass accent-card rounded-2xl p-6">
          <h2 class="section-title">Top 5 produtos</h2>
          @if (top().length === 0) {
            <p class="empty">Sem vendas no período.</p>
          } @else {
            <ol class="space-y-3">
              @for (p of top(); track p.productName; let i = $index) {
                <li class="flex items-center gap-4">
                  <span class="font-mono text-xs text-neutral-500 w-6">{{ (i + 1).toString().padStart(2, '0') }}</span>
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center justify-between gap-2 text-sm">
                      <span class="truncate">{{ p.productName }}</span>
                      <span class="flex items-center gap-2 shrink-0">
                        @if (i === 0) { <span class="badge badge-red">Em alta</span> }
                        <span class="font-display font-bold tabular-nums">{{ p.totalSold }}</span>
                      </span>
                    </div>
                    <div class="mt-1.5 h-1.5 rounded-full bg-white/5 overflow-hidden">
                      <div class="h-full rounded-full bg-gradient-to-r from-brand-600 via-brand-400 to-gold-400 transition-all duration-700"
                        [style.width.%]="(p.totalSold / topMax()) * 100"></div>
                    </div>
                  </div>
                </li>
              }
            </ol>
          }
        </section>
      </div>

      <section class="reveal reveal-3">
        <h2 class="section-title">Estoque abaixo do mínimo</h2>
        @if (low().length === 0) {
          <div class="glass rounded-2xl"><p class="empty">Nenhum item abaixo do mínimo.</p></div>
        } @else {
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            @for (i of low(); track i.id) {
              <div class="rounded-2xl border border-red-800/50 bg-red-950/30 p-4">
                <div class="flex items-center justify-between">
                  <p class="font-medium">{{ i.name }}</p>
                  <span class="badge" [class]="i.currentQuantity <= 0 ? 'badge-red' : 'badge-yellow'">{{ i.currentQuantity <= 0 ? 'Zerado' : 'Baixo' }}</span>
                </div>
                <p class="mt-2 font-display text-2xl font-bold text-red-300 tabular-nums">{{ i.currentQuantity }} <span class="text-sm text-neutral-500">{{ i.unitOfMeasure }}</span></p>
                <p class="kpi-label mt-1">Mínimo {{ i.minimumQuantity }} {{ i.unitOfMeasure }}</p>
              </div>
            }
          </div>
        }
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
  topMax = computed(() => Math.max(1, ...this.top().map((p) => p.totalSold)));
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
