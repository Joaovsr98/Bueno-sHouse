import { Component, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { CartService } from '../core/cart.service';
import { currency } from '../core/format';
import { Icon } from './icon';

/**
 * Carrinho sempre a mao nas telas do cliente: aparece no canto assim que ha item,
 * com quantidade, total e o atalho para fechar o pedido. Ao adicionar, pula e mostra
 * "+1 X-Burguer" (no lugar de um aviso por clique, que empilhava na tela).
 */
@Component({
  selector: 'app-floating-cart',
  imports: [RouterLink, Icon],
  template: `
    @if (visible()) {
      <div class="flex flex-col items-end gap-2 pointer-events-none">
        @if (bubble(); as text) {
          <span class="added-bubble pointer-events-none rounded-full bg-ink-850 border border-brand-500/40 px-3 py-1 text-xs text-brand-200 shadow-lg" aria-live="polite">
            {{ text }}
          </span>
        }
        <a routerLink="/app/carrinho" [class.bump]="bumping()"
          class="pointer-events-auto w-full sm:w-auto flex items-center gap-4 rounded-2xl btn-brand px-5 py-3 shadow-[0_20px_50px_-15px_rgba(255,106,26,.7)]"
          [attr.aria-label]="'Ver carrinho: ' + count() + ' itens, ' + currency(subtotal())">
          <span class="relative">
            <app-icon name="cart" [size]="22" />
            <span class="absolute -top-2 -right-2.5 grid place-items-center min-w-5 h-5 px-1 rounded-full bg-ink-950 text-brand-300 text-[11px] font-bold">{{ count() }}</span>
          </span>
          <span class="flex-1 leading-tight">
            <span class="block text-[11px] font-semibold uppercase tracking-wide opacity-80">Seu pedido</span>
            <span class="block font-display font-bold">{{ currency(subtotal()) }}</span>
          </span>
          <span class="inline-flex items-center gap-1.5 text-sm font-semibold">Ver carrinho <app-icon name="arrow" [size]="16" /></span>
        </a>
      </div>
    }
  `,
  styles: [`
    .bump { animation: bump .45s cubic-bezier(.2,.9,.3,1.4); }
    @keyframes bump { 0% { transform: scale(1); } 35% { transform: scale(1.07); } 100% { transform: scale(1); } }
    .added-bubble { animation: bubble 1.8s ease forwards; }
    @keyframes bubble {
      0% { opacity: 0; transform: translateY(8px); }
      15%, 75% { opacity: 1; transform: translateY(0); }
      100% { opacity: 0; transform: translateY(-6px); }
    }
    @media (prefers-reduced-motion: reduce) { .bump, .added-bubble { animation: none; } }
  `],
})
export class FloatingCart {
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);

  readonly currency = currency;
  readonly count = this.cart.count;
  readonly subtotal = this.cart.subtotal;

  private readonly url = toSignal(
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), map(() => this.router.url)),
    { initialValue: this.router.url },
  );
  /** Some no proprio carrinho/checkout e no acompanhamento (la ja nao faz sentido). */
  readonly visible = computed(() => this.count() > 0 && !/\/app\/(carrinho|pedidos)/.test(this.url()));

  readonly bumping = signal(false);
  readonly bubble = signal<string | null>(null);
  private timers: ReturnType<typeof setTimeout>[] = [];

  constructor() {
    effect(() => {
      const added = this.cart.lastAdded();
      if (!added) return;
      this.timers.forEach(clearTimeout);
      this.bumping.set(false);
      this.bubble.set(null);
      // reinicia as animacoes mesmo com cliques seguidos
      this.timers = [
        setTimeout(() => { this.bumping.set(true); this.bubble.set(added.text); }),
        setTimeout(() => this.bumping.set(false), 500),
        setTimeout(() => this.bubble.set(null), 1900),
      ];
    });
  }
}
