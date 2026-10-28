import { Component, input } from '@angular/core';

/**
 * Indicador de carregamento "bocas do fogao": tres pontos que acendem em
 * sequencia, o mesmo desenho da abertura. `size` em px de cada boca.
 */
@Component({
  selector: 'app-burners',
  template: `
    <span class="inline-flex items-center" [style.gap.px]="size() * 0.7" role="status" aria-label="Carregando">
      @for (b of [0, 1, 2]; track b) {
        <span class="burner" [style.width.px]="size()" [style.height.px]="size()" [style.animation-delay.ms]="b * 220"></span>
      }
    </span>
  `,
  styles: [`
    .burner {
      border-radius: 999px; border: 2px solid rgba(255,255,255,.14); background: #11141c;
      animation: burner-light 1.32s ease-in-out infinite;
    }
    @keyframes burner-light {
      0%, 100% { background: #11141c; border-color: rgba(255,255,255,.14); box-shadow: none; }
      35%, 55% {
        border-color: #ff8c3a; box-shadow: 0 0 16px 3px rgba(255,106,26,.55);
        background: radial-gradient(circle at 50% 60%, #f5b544, #ff6a1a 55%, #e8500c);
      }
    }
    @media (prefers-reduced-motion: reduce) { .burner { animation-duration: 3s; } }
  `],
})
export class Burners {
  readonly size = input(14);
}
