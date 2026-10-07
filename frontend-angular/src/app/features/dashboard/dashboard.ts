import { Component, computed, inject, signal, effect, untracked } from '@angular/core';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ChartComponent } from './chart';

interface DailyRevenue { day: string; revenue: number; }
interface TopProduct { productName: string; totalSold: number; }
interface LowStock { id: number; name: string; currentQuantity: number; minimumQuantity: number; unitOfMeasure: string; }

@Component({
  selector: 'app-dashboard',
  host: { class: 'page-basic' },
  imports: [ChartComponent],
  template: `
    <h1>Dashboard</h1>
    @if (error()) { <p>{{ error() }}</p> }

    <p>Ticket médio: {{ avgTicket() ?? '-' }} ({{ orderCount() }} pedidos)</p>
    <p>Food cost médio: {{ foodCost() ?? '-' }}%</p>

    <h2>Faturamento por dia</h2>
    <app-chart type="line" label="Faturamento" [labels]="revenueLabels()" [values]="revenueValues()" />
    <table border="1">
      <tr><th>Dia</th><th>Faturamento</th></tr>
      @for (r of revenue(); track r.day) { <tr><td>{{ r.day }}</td><td>{{ r.revenue }}</td></tr> }
    </table>

    <h2>Top 5 produtos</h2>
    <app-chart type="bar" label="Vendidos" [labels]="topLabels()" [values]="topValues()" />
    <table border="1">
      <tr><th>Produto</th><th>Vendidos</th></tr>
      @for (p of top(); track p.productName) { <tr><td>{{ p.productName }}</td><td>{{ p.totalSold }}</td></tr> }
    </table>

    <h2>Estoque baixo</h2>
    <table border="1">
      <tr><th>Item</th><th>Atual</th><th>Mínimo</th></tr>
      @for (i of low(); track i.id) {
        <tr><td>{{ i.name }}</td><td>{{ i.currentQuantity }} {{ i.unitOfMeasure }}</td><td>{{ i.minimumQuantity }}</td></tr>
      }
    </table>
  `,
})
export class Dashboard {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

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
