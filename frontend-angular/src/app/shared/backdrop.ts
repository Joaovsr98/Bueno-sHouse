import { Component, input } from '@angular/core';

/**
 * Fundo "capa premium": a marca gravada em tamanho gigante (telhado, B, pao,
 * hamburguer e chama em relevo escuro com borda de brasa), brilho quente nos
 * cantos e faiscas subindo. Fica atras do conteudo e nao recebe cliques.
 * `align` posiciona o emblema: no centro (pagina inicial) ou a direita (login).
 */
@Component({
  selector: 'app-backdrop',
  template: `
    <div class="absolute inset-0 overflow-hidden pointer-events-none" aria-hidden="true">
      <div class="glow glow-a"></div>
      <div class="glow glow-b"></div>

      <svg viewBox="0 0 100 100" class="engraved" [class.engraved-center]="align() === 'center'"
        [class.engraved-right]="align() === 'right'">
        <defs>
          <linearGradient id="bd-face" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#2a1a12" /><stop offset=".6" stop-color="#160f0c" /><stop offset="1" stop-color="#0d0a09" />
          </linearGradient>
          <linearGradient id="bd-edge" x1="1" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="#ff8c3a" stop-opacity=".7" /><stop offset=".5" stop-color="#e8500c" stop-opacity=".35" />
            <stop offset="1" stop-color="#e8500c" stop-opacity=".05" />
          </linearGradient>
          <filter id="bd-soft" x="-10%" y="-10%" width="120%" height="120%">
            <feGaussianBlur stdDeviation=".35" />
          </filter>
        </defs>

        <!-- orbitas -->
        <circle cx="50" cy="50" r="47" fill="none" stroke="url(#bd-edge)" stroke-width=".25" />
        <path d="M21.1 78.9 A38 38 0 1 1 67 19.1" fill="none" stroke="url(#bd-edge)" stroke-width="1.6" stroke-linecap="round" />

        <g stroke="url(#bd-edge)" stroke-width=".55" filter="url(#bd-soft)">
          <!-- telhado e coluna do B -->
          <path d="M24 40 L50 19 L76 40 L71 44 L50 27 L29 44 Z" fill="url(#bd-face)" stroke-linejoin="round" />
          <rect x="31" y="37" width="10" height="47" rx="3" fill="url(#bd-face)" />
          <!-- pao de cima com gergelim -->
          <path d="M41 37 h11 c11 0 17 5 17 11.5 c0 6.5 -6 11.5 -17 11.5 h-11 z" fill="url(#bd-face)" />
          <!-- hamburguer e folha -->
          <path d="M41 62 h14 c9 0 15 2.2 15 5 c0 2.8 -6 5 -15 5 h-14 z" fill="url(#bd-face)" />
          <path d="M41 64 q6 -3 12 0 t12 0 t6 1" fill="none" />
          <!-- pao de baixo -->
          <path d="M41 74 h13 c10 0 16 2.3 16 5 c0 2.7 -6 5 -16 5 h-13 z" fill="url(#bd-face)" />
          <!-- chama -->
          <path d="M76 86 c6 -5 7 -10 3 -16 c0 4 -2 5 -3.5 6 c.5 -4 -1 -7 -4 -9 c1 4 -1.5 7 -3 10 c-1.5 3 -.5 7 2.5 9 z"
            fill="url(#bd-face)" />
        </g>
      </svg>

      @for (s of sparks; track $index) {
        <span class="spark" [style.left.%]="s.x" [style.animation-delay.s]="s.delay" [style.animation-duration.s]="s.dur"></span>
      }
      <div class="vignette"></div>
    </div>
  `,
  styles: [`
    .glow { position: absolute; border-radius: 999px; filter: blur(10px); }
    .glow-a {
      width: 60vmax; height: 60vmax; left: -22vmax; bottom: -30vmax;
      background: radial-gradient(closest-side, rgba(232,80,12,.42), rgba(232,80,12,.12) 55%, transparent);
    }
    .glow-b {
      width: 50vmax; height: 50vmax; right: -20vmax; top: -26vmax;
      background: radial-gradient(closest-side, rgba(255,140,58,.22), transparent 70%);
    }
    .engraved { position: absolute; width: min(64rem, 120vmin); height: auto; opacity: .6;
      filter: drop-shadow(0 0 30px rgba(232,80,12,.12)); }
    .engraved-center { left: 26%; top: 54%; width: min(76rem, 135vmin); transform: translate(-50%, -50%); }
    .engraved-right { right: -14rem; top: 50%; transform: translateY(-50%); }
    .spark {
      position: absolute; bottom: -10px; width: 3px; height: 3px; border-radius: 999px;
      background: #ffb347; box-shadow: 0 0 8px 2px rgba(255,106,26,.7); opacity: 0;
    }
    .vignette { position: absolute; inset: 0;
      background: radial-gradient(ellipse at center, transparent 45%, rgba(7,8,12,.75) 100%); }
    @media (prefers-reduced-motion: no-preference) {
      .spark { animation: rise linear infinite; }
      .engraved { animation: smolder 8s ease-in-out infinite; }
    }
    @keyframes rise {
      0% { transform: translate(0, 0); opacity: 0; }
      12% { opacity: .9; }
      100% { transform: translate(30px, -85vh); opacity: 0; }
    }
    @keyframes smolder { 0%, 100% { opacity: .5; } 50% { opacity: .68; } }
  `],
})
export class Backdrop {
  readonly align = input<'center' | 'right'>('center');

  readonly sparks = Array.from({ length: 14 }, (_, i) => ({
    x: (i * 37) % 100,
    delay: (i * 0.9) % 9,
    dur: 7 + ((i * 13) % 6),
  }));
}
