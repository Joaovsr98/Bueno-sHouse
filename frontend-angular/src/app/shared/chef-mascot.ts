import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { CartService } from '../core/cart.service';

/** De quanto em quanto tempo o chapeiro abre a bandeja sozinho, e por quanto tempo. */
const AUTO_EVERY_MS = 9000;
const OPEN_FOR_MS = 2600;

/**
 * Chapeiro do Bueno's House: miniatura 3D que fica no canto das telas do cliente.
 * Balanca parado com a bandeja fechada e, de tempos em tempos (ou ao passar o mouse
 * / tocar), levanta a tampa e mostra o hamburguer fumegante, com um balao que muda
 * conforme a tela. Com itens no carrinho, o clique leva para fechar o pedido.
 */
@Component({
  selector: 'app-chef-mascot',
  template: `
    <button type="button" class="chef relative block w-[5.5rem] h-[12rem] cursor-pointer select-none"
      (mouseenter)="open(true)" (mouseleave)="open(false)" (focus)="open(true)" (blur)="open(false)" (click)="onClick()"
      [attr.aria-label]="label()">
      @if (opened()) {
        <span class="bubble absolute -top-1 right-[85%] whitespace-nowrap rounded-2xl rounded-br-sm bg-white text-ink-950 text-xs font-semibold px-3 py-2 shadow-xl" aria-live="polite">
          {{ message() }}
        </span>
      }
      <span class="idle absolute inset-0 block origin-bottom">
        <img src="chef-fechado.webp" alt="" aria-hidden="true" draggable="false"
          class="frame absolute inset-0 w-full h-full object-contain drop-shadow-[0_18px_22px_rgba(0,0,0,.55)]"
          [class.is-hidden]="opened()" />
        <img src="chef-aberto.webp" alt="" aria-hidden="true" draggable="false"
          class="frame absolute inset-0 w-full h-full object-contain drop-shadow-[0_18px_22px_rgba(0,0,0,.55)]"
          [class.is-hidden]="!opened()" />
        <span class="glow absolute left-1/2 top-[34%] w-24 h-16 -translate-x-1/2 rounded-full" [class.on]="opened()"></span>
      </span>
    </button>
  `,
  styles: [`
    .chef { transition: transform .3s ease-out; }
    .chef:hover { transform: scale(1.12); }
    .frame { transition: opacity .35s ease, transform .35s ease; }
    .frame.is-hidden { opacity: 0; transform: translateY(4px) scale(.98); }
    /* calor saindo do hamburguer quando a tampa abre */
    .glow { background: radial-gradient(closest-side, rgba(255,140,58,.55), transparent); opacity: 0;
      filter: blur(8px); transition: opacity .4s ease; pointer-events: none; }
    .glow.on { opacity: 1; }
    .bubble { animation: pop .35s cubic-bezier(.2,.9,.3,1.4); }
    @keyframes pop { from { opacity: 0; transform: translateY(6px) scale(.9); } to { opacity: 1; transform: none; } }
    @media (prefers-reduced-motion: no-preference) {
      .idle { animation: sway 4.5s ease-in-out infinite; }
    }
    @keyframes sway {
      0%, 100% { transform: rotate(0deg) translateY(0); }
      25% { transform: rotate(-1.6deg) translateY(-2px); }
      75% { transform: rotate(1.6deg) translateY(-1px); }
    }
  `],
})
export class ChefMascot implements OnDestroy {
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);

  readonly opened = signal(false);
  private hovering = false;
  private readonly url = toSignal(
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), map(() => this.router.url)),
    { initialValue: this.router.url },
  );

  /** Fala conforme o momento: pedido em andamento, carrinho com itens ou so olhando. */
  readonly message = computed(() => {
    if (/\/app\/pedidos\//.test(this.url())) return 'Tá saindo da chapa! 🔥';
    if (this.cart.count() > 0) return 'Bora fechar esse pedido? 🍔';
    return 'Bateu a fome? 🍔';
  });
  readonly label = computed(() =>
    this.cart.count() > 0 ? 'Chapeiro do Bueno\'s House: ir para o carrinho' : 'Chapeiro do Bueno\'s House');

  private readonly auto: ReturnType<typeof setInterval> | null;
  private closeTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    const reduced = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.auto = reduced ? null : setInterval(() => this.peek(), AUTO_EVERY_MS);
    // primeira abertura logo depois de entrar, para a pessoa notar o chapeiro
    if (!reduced) this.closeTimer = setTimeout(() => this.peek(), 1800);
  }

  open(on: boolean): void {
    this.hovering = on;
    if (this.closeTimer) clearTimeout(this.closeTimer);
    this.opened.set(on);
  }

  onClick(): void {
    if (this.cart.count() > 0 && !/\/app\/carrinho/.test(this.url())) {
      this.router.navigateByUrl('/app/carrinho');
      return;
    }
    this.peek();
  }

  /** Abre a bandeja por um instante (sem atrapalhar quem esta com o mouse em cima). */
  private peek(): void {
    if (this.hovering || document.hidden) return;
    this.opened.set(true);
    if (this.closeTimer) clearTimeout(this.closeTimer);
    this.closeTimer = setTimeout(() => { if (!this.hovering) this.opened.set(false); }, OPEN_FOR_MS);
  }

  ngOnDestroy(): void {
    if (this.auto) clearInterval(this.auto);
    if (this.closeTimer) clearTimeout(this.closeTimer);
  }
}
