import { Component, ElementRef, OnDestroy, effect, input, viewChild } from '@angular/core';
import { BarController, BarElement, CategoryScale, Chart, ChartConfiguration, ChartType, Filler, Legend, LineController, LineElement, LinearScale, PointElement, Tooltip } from 'chart.js';

Chart.register(BarController, BarElement, LineController, LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Legend, Filler);

const BRAND = '#f97316';
const TICK = '#8b91a3';
const GRID = 'rgba(255, 255, 255, 0.06)';

/**
 * Gráfico genérico (linha ou barra) sobre Chart.js puro, no tema escuro/laranja do sistema.
 * Redesenha quando labels/values mudam. O container define a altura (h-64).
 */
@Component({
  selector: 'app-chart',
  template: `<div class="relative h-64 w-full"><canvas #canvas></canvas></div>`,
})
export class ChartComponent implements OnDestroy {
  type = input<Extract<ChartType, 'line' | 'bar'>>('bar');
  labels = input<string[]>([]);
  values = input<number[]>([]);
  label = input('');
  /** Formata o valor no tooltip e no eixo Y (ex.: moeda). */
  formatValue = input<(v: number) => string>((v) => String(v));

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;

  constructor() {
    effect(() => {
      const isLine = this.type() === 'line';
      const fmt = this.formatValue();
      const cfg: ChartConfiguration<'line' | 'bar'> = {
        type: this.type(),
        data: {
          labels: this.labels(),
          datasets: [
            {
              label: this.label(),
              data: this.values(),
              borderColor: BRAND,
              backgroundColor: isLine ? 'rgba(249, 115, 22, 0.15)' : 'rgba(249, 115, 22, 0.75)',
              borderWidth: 2,
              tension: 0.3,
              fill: isLine,
              pointRadius: isLine ? 3 : 0,
              pointBackgroundColor: BRAND,
              borderRadius: isLine ? 0 : 6,
            },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: { callbacks: { label: (ctx) => ` ${this.label()}: ${fmt(Number(ctx.parsed.y))}` } },
          },
          scales: {
            x: { ticks: { color: TICK }, grid: { display: false } },
            y: { beginAtZero: true, ticks: { color: TICK, callback: (v) => fmt(Number(v)) }, grid: { color: GRID } },
          },
        },
      };
      this.chart?.destroy();
      this.chart = new Chart(this.canvas().nativeElement, cfg);
    });
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }
}
