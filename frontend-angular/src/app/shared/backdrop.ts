import { Component, input } from '@angular/core';

/**
 * Fundo "capa premium": a propria logo gravada em tamanho grande (aro, telhado, B,
 * pao e hamburguer em relevo escuro com luz de brasa na borda), brilho quente nos
 * cantos e faiscas subindo. Fica atras do conteudo e nao recebe cliques.
 * `align` posiciona o emblema: no centro (pagina inicial) ou a direita (login).
 */
@Component({
  selector: 'app-backdrop',
  template: `
    <div class="absolute inset-0 overflow-hidden pointer-events-none" aria-hidden="true">
      <div class="glow glow-a"></div>
      <div class="glow glow-b"></div>

      <svg viewBox="-4 -4 108 108" class="engraved" [class.engraved-center]="align() === 'center'"
        [class.engraved-right]="align() === 'right'">
        <defs>
          <linearGradient id="bd-face" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#2b1b12" /><stop offset=".55" stop-color="#17100c" /><stop offset="1" stop-color="#0c0a09" />
          </linearGradient>
          <!-- A propria logo (mesma geometria do cabecalho), so com formas cheias: sem contornos soltos. -->
          <g id="bd-mark">
            <path d="M21.1 78.9 A38 38 0 1 1 67 19.1" fill="none" stroke-width="5.5" stroke-linecap="round" />
            <path d="M27 39 L50 21 L73 39" fill="none" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" />
            <circle cx="74" cy="15.5" r="3.6" stroke="none" /><circle cx="82" cy="12" r="2" stroke="none" />
            <rect x="31" y="37" width="10" height="47" rx="3" stroke="none" />
            <path d="M41 37 h11 c11 0 17 5 17 11.5 c0 6.5 -6 11.5 -17 11.5 h-11 z" stroke="none" />
            <path d="M41 62 h14 c9 0 15 2.2 15 5 c0 2.8 -6 5 -15 5 h-14 z" stroke="none" />
            <path d="M41 74 h13 c10 0 16 2.3 16 5 c0 2.7 -6 5 -16 5 h-13 z" stroke="none" />
          </g>
        </defs>

        <!-- selo: um aro fino em volta -->
        <circle cx="50" cy="50" r="51" fill="none" stroke="#ff8c3a" stroke-opacity=".14" stroke-width=".3" />
        <!-- luz de brasa na borda de cima/direita, depois a face escura em relevo -->
        <use href="#bd-mark" fill="#ff7a24" stroke="#ff7a24" opacity=".55" transform="translate(.5 -.5)" />
        <use href="#bd-mark" fill="url(#bd-face)" stroke="url(#bd-face)" />
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
    .engraved { position: absolute; width: min(58rem, 110vmin); height: auto; opacity: .75;
      filter: drop-shadow(0 0 30px rgba(232,80,12,.12)); }
    .engraved-center { left: 50%; top: 50%; width: min(48rem, 88vmin); transform: translate(-50%, -50%); }
    .engraved-right { right: 2.5rem; top: 50%; width: min(34rem, 60vmin); transform: translateY(-50%); }
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
    @keyframes smolder { 0%, 100% { opacity: .65; } 50% { opacity: .85; } }
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
