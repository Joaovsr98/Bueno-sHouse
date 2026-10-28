import { Component, computed, input } from '@angular/core';

/**
 * Anel de pontuacao (padrao ArtistFound) para percentuais como o food cost.
 * Cor pelos limites do RN02: <=30 verde, ate 35 amarelo, acima vermelho.
 */
@Component({
  selector: 'app-ring',
  template: `
    <span class="relative inline-grid place-items-center" [style.width.px]="size()" [style.height.px]="size()">
      <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 44 44" class="-rotate-90">
        <circle cx="22" cy="22" r="18" fill="none" stroke="rgba(255,255,255,.08)" stroke-width="4" />
        <circle cx="22" cy="22" r="18" fill="none" [attr.stroke]="color()" stroke-width="4" stroke-linecap="round"
          [attr.stroke-dasharray]="circumference" [attr.stroke-dashoffset]="offset()" class="ring-arc" />
      </svg>
      <span class="absolute font-display font-bold tabular-nums" [style.font-size.px]="size() * 0.26" [style.color]="color()">
        {{ label() }}
      </span>
    </span>
  `,
  styles: [`.ring-arc { transition: stroke-dashoffset 1s cubic-bezier(0.22, 1, 0.36, 1); }`],
})
export class Ring {
  /** Percentual 0-100 (null = sem dado). */
  readonly value = input<number | null>(null);
  readonly size = input(56);
  /** Escala do anel: 100% do anel equivale a este valor (food cost cheio em 60%). */
  readonly max = input(60);

  readonly circumference = 2 * Math.PI * 18;
  readonly color = computed(() => {
    const v = this.value();
    if (v == null) return '#5b6172';
    return v <= 30 ? '#4ade80' : v <= 35 ? '#facc15' : '#f87171';
  });
  readonly offset = computed(() => {
    const v = Math.min(Math.max(this.value() ?? 0, 0), this.max());
    return this.circumference * (1 - v / this.max());
  });
  readonly label = computed(() => {
    const v = this.value();
    return v == null ? '—' : `${Math.round(v)}%`;
  });
}
