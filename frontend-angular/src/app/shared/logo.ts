import { Component, input } from '@angular/core';

let nextId = 0;

/**
 * Marca do Bueno's House: o telhado (House) coroa um "B" cujas curvas sao o
 * pao com gergelim e o hamburguer, dentro de um arco de brasa com faiscas.
 * `tagline` vazio esconde a linha de baixo; `wordmark=false` mostra so o simbolo.
 */
@Component({
  selector: 'app-logo',
  template: `
    <span class="inline-flex items-center gap-3">
      <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 100 100" role="img" aria-label="Bueno's House"
        class="shrink-0" style="color: var(--logo-house, #f3ead9)">
        <defs>
          <linearGradient [attr.id]="ids.ring" x1="0" y1="1" x2="1" y2="0">
            <stop offset="0" stop-color="#e8500c" /><stop offset=".55" stop-color="#ff8c3a" /><stop offset="1" stop-color="#f5b544" />
          </linearGradient>
          <linearGradient [attr.id]="ids.bun" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="#ffb347" /><stop offset="1" stop-color="#e8770f" />
          </linearGradient>
        </defs>
        <path d="M21.1 78.9 A38 38 0 1 1 67 19.1" fill="none" [attr.stroke]="'url(#' + ids.ring + ')'" stroke-width="5.5" stroke-linecap="round" />
        <circle cx="74" cy="15.5" r="3.6" fill="#f5b544" class="logo-spark" />
        <circle cx="82" cy="12" r="2" fill="#ffd27a" class="logo-spark logo-spark-2" />
        <path d="M27 39 L50 21 L73 39" fill="none" stroke="currentColor" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" />
        <rect x="31" y="37" width="10" height="47" rx="3" fill="currentColor" />
        <path d="M41 37 h11 c11 0 17 5 17 11.5 c0 6.5 -6 11.5 -17 11.5 h-11 z" [attr.fill]="'url(#' + ids.bun + ')'" />
        <circle cx="52" cy="44" r="1.6" fill="#fff4dc" /><circle cx="59" cy="47" r="1.6" fill="#fff4dc" /><circle cx="51" cy="51" r="1.6" fill="#fff4dc" />
        <path d="M41 62 h14 c9 0 15 2.2 15 5 c0 2.8 -6 5 -15 5 h-14 z" fill="#5a2a12" />
        <path d="M41 64 q6 -3 12 0 t12 0 t6 1" fill="none" stroke="#7bc96f" stroke-width="2.4" stroke-linecap="round" />
        <path d="M41 74 h13 c10 0 16 2.3 16 5 c0 2.7 -6 5 -16 5 h-13 z" [attr.fill]="'url(#' + ids.bun + ')'" />
      </svg>
      @if (wordmark()) {
        <span class="leading-none">
          <span class="block font-display font-bold tracking-tight" [style.font-size.px]="size() * 0.42">Bueno's House</span>
          @if (tagline()) {
            <span class="block eyebrow-plain mt-1.5" [style.font-size.px]="Math.max(9, size() * 0.15)">{{ tagline() }}</span>
          }
        </span>
      }
    </span>
  `,
})
export class Logo {
  readonly size = input(44);
  readonly wordmark = input(true);
  readonly tagline = input('Cozinha · Gestão');

  readonly Math = Math;
  /** ids unicos por instancia: varias logos na mesma pagina nao disputam o mesmo gradiente. */
  readonly ids = { ring: `bh-ring-${++nextId}`, bun: `bh-bun-${nextId}` };
}
