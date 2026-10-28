import { Component, effect, input, signal, untracked } from '@angular/core';

/** Numero que sobe ate o valor final ao aparecer (padrao dos contadores do ArtistFound/THV). */
@Component({
  selector: 'app-count-up',
  template: `{{ format()(display()) }}`,
})
export class CountUp {
  readonly value = input<number>(0);
  readonly format = input<(v: number) => string>((v) => Math.round(v).toLocaleString('pt-BR'));
  readonly duration = input(900);

  readonly display = signal(0);

  constructor() {
    effect(() => {
      const target = Number(this.value() ?? 0);
      untracked(() => this.animate(target));
    });
  }

  private animate(target: number): void {
    const reduce = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    const from = this.display();
    if (reduce || from === target) {
      this.display.set(target);
      return;
    }
    const start = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / this.duration());
      const eased = 1 - Math.pow(1 - t, 3);
      this.display.set(from + (target - from) * eased);
      if (t < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
}
