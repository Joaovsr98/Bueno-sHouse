import { Component, ElementRef, OnDestroy, effect, input, viewChild } from '@angular/core';
import { BarController, BarElement, CategoryScale, Chart, ChartType, Legend, LineController, LineElement, LinearScale, PointElement, Tooltip } from 'chart.js';

Chart.register(BarController, BarElement, LineController, LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Legend);

/** Gráfico genérico (linha ou barra) sobre Chart.js puro — redesenha quando labels/values mudam. */
@Component({
  selector: 'app-chart',
  template: `<canvas #canvas></canvas>`,
})
export class ChartComponent implements OnDestroy {
  type = input<Extract<ChartType, 'line' | 'bar'>>('bar');
  labels = input<string[]>([]);
  values = input<number[]>([]);
  label = input('');

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;

  constructor() {
    effect(() => {
      const cfg = {
        type: this.type(),
        data: { labels: this.labels(), datasets: [{ label: this.label(), data: this.values(), borderWidth: 2, tension: 0.25 }] },
        options: { responsive: true, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } },
      };
      this.chart?.destroy();
      this.chart = new Chart(this.canvas().nativeElement, cfg);
    });
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }
}
