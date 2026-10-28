import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { defaultRouteForRole } from '../core/routing';
import { Logo } from './logo';
import { Icon } from './icon';
import { Backdrop } from './backdrop';

const SEEN_KEY = 'bueno.intro.seen';
const BURNERS = 3;

/**
 * Abertura "esquentando a chapa" (inspirada na largada do Paddock 22): as bocas
 * acendem uma a uma e, com a chapa quente, a pessoa escolhe o lado: matar a fome
 * (cardapio) ou acender a chapa (equipe). Aparece toda vez que se entra pela
 * pagina inicial; em outra tela so no primeiro acesso da sessao, e ai fecha
 * sozinha. Quem prefere menos movimento so ve a abertura na pagina inicial.
 */
@Component({
  selector: 'app-intro',
  imports: [Logo, Icon, Backdrop],
  template: `
    @if (visible()) {
      <div class="fixed inset-0 z-[100] bg-ink-950 transition-opacity duration-500 overflow-y-auto"
        [class.opacity-0]="leaving()" role="dialog" aria-label="Abertura">
        <app-backdrop align="center" />

        <div class="relative min-h-full flex flex-col items-center justify-center gap-6 sm:gap-8 px-6 py-10 sm:py-16 text-center">
          <app-logo [size]="64" [wordmark]="false" />
          <div class="flex gap-5">
            @for (b of burners; track b) {
              <span class="burner" [class.on]="lit() > b"></span>
            }
          </div>
          <p class="eyebrow-plain text-[0.72rem]" [class.text-brand-400]="hot()">
            {{ hot() ? 'Chapa quente' : 'Esquentando a chapa' }}
          </p>

          @if (choosing()) {
            <div class="choice-in flex flex-col items-center gap-6">
              <h1 class="text-4xl sm:text-6xl font-bold uppercase leading-[0.95]">
                O que você<br /><span class="text-ember">quer?</span>
              </h1>
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 w-full max-w-2xl">
                <button (click)="choose(customerLink())" class="side">
                  <span class="side-icon"><app-icon name="cart" [size]="22" /></span>
                  <span class="eyebrow-plain text-[0.6rem]">Para você</span>
                  <span class="block font-display text-xl font-bold uppercase mt-1">Quero matar minha fome</span>
                  <span class="side-cta">{{ profileIsCustomer() ? 'Ver o cardápio' : 'Entrar ou criar conta' }} <app-icon name="arrow" [size]="15" /></span>
                </button>
                <button (click)="choose(staffLink())" class="side">
                  <span class="side-icon"><app-icon name="chef" [size]="22" /></span>
                  <span class="eyebrow-plain text-[0.6rem]">Para a equipe</span>
                  <span class="block font-display text-xl font-bold uppercase mt-1">Bora acender a chapa</span>
                  <span class="side-cta">Entrar no sistema <app-icon name="arrow" [size]="15" /></span>
                </button>
              </div>
            </div>
          }
        </div>

        <button (click)="finish()" class="relative block mx-auto mb-8 sm:mb-0 sm:absolute sm:bottom-6 sm:right-6 eyebrow-plain text-[0.65rem] hover:text-neutral-200">
          {{ choosing() ? 'Ver a página inicial →' : 'Pular →' }}
        </button>
      </div>
    }
  `,
  styles: [`
    .burner {
      width: 26px; height: 26px; border-radius: 999px;
      border: 2px solid rgba(255,255,255,.12); background: #11141c;
      transition: background .25s ease, box-shadow .25s ease, border-color .25s ease;
    }
    .burner.on {
      border-color: #ff8c3a;
      background: radial-gradient(circle at 50% 60%, #f5b544, #ff6a1a 55%, #e8500c);
      box-shadow: 0 0 22px 4px rgba(255,106,26,.55);
    }
    .choice-in { animation: choice-in .6s cubic-bezier(.2,.8,.2,1) both; }
    @keyframes choice-in { from { opacity: 0; transform: translateY(14px); } to { opacity: 1; transform: none; } }
    .side {
      position: relative; display: block; text-align: left; padding: 1.5rem; border-radius: 1.25rem;
      background: linear-gradient(180deg, rgba(255,255,255,.05), rgba(255,255,255,.015));
      border: 1px solid rgba(255,255,255,.1); backdrop-filter: blur(6px);
      transition: border-color .25s ease, transform .25s ease, box-shadow .25s ease;
    }
    .side:hover, .side:focus-visible {
      border-color: rgba(255,106,26,.55); transform: translateY(-3px);
      box-shadow: 0 30px 60px -30px rgba(255,106,26,.5);
    }
    .side-icon {
      display: grid; place-items: center; width: 2.75rem; height: 2.75rem; margin-bottom: 1rem;
      border-radius: .9rem; color: #ff8c3a; background: rgba(255,106,26,.1); border: 1px solid rgba(255,106,26,.25);
    }
    .side-cta { display: inline-flex; align-items: center; gap: .45rem; margin-top: 1rem; font-weight: 600; font-size: .85rem; color: #ff8c3a; }
    @media (prefers-reduced-motion: reduce) { .choice-in { animation: none; } }
  `],
})
export class Intro implements OnDestroy {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  readonly burners = Array.from({ length: BURNERS }, (_, i) => i);
  readonly visible = signal(false);
  readonly leaving = signal(false);
  readonly lit = signal(0);
  readonly hot = computed(() => this.lit() >= BURNERS);
  /** Na pagina inicial a abertura termina na escolha do lado, em vez de fechar sozinha. */
  readonly choosing = signal(false);

  private readonly profile = computed(() => this.auth.user()?.profileName);
  readonly profileIsCustomer = computed(() => this.profile() === 'CLIENTE');
  /** Cliente logado vai direto ao cardapio; os demais entram ou criam conta e voltam para ele. */
  readonly customerLink = computed(() =>
    this.profile() === 'CLIENTE' ? '/app/cardapio' : '/login?returnUrl=%2Fapp%2Fcardapio',
  );
  readonly staffLink = computed(() => {
    const p = this.profile();
    return p && p !== 'CLIENTE' ? defaultRouteForRole(p) : '/login?lado=equipe';
  });

  private timers: ReturnType<typeof setTimeout>[] = [];

  constructor() {
    const home = this.onHome();
    if (!home && (this.alreadySeen() || this.prefersReducedMotion())) return;
    this.visible.set(true);
    const step = home && this.prefersReducedMotion() ? 120 : 380;
    this.burners.forEach((i) => this.timers.push(setTimeout(() => this.lit.set(i + 1), 350 + i * step)));
    const lit = 350 + BURNERS * step;
    this.timers.push(setTimeout(() => (home ? this.choosing.set(true) : this.finish()), lit + 500));
  }

  choose(link: string): void {
    this.router.navigateByUrl(link);
    this.finish();
  }

  finish(): void {
    this.markSeen();
    this.leaving.set(true);
    this.timers.push(setTimeout(() => this.visible.set(false), 500));
  }

  ngOnDestroy(): void {
    this.timers.forEach(clearTimeout);
  }

  private onHome(): boolean {
    return typeof location !== 'undefined' && location.pathname === '/';
  }

  private alreadySeen(): boolean {
    try {
      return sessionStorage.getItem(SEEN_KEY) === '1';
    } catch {
      return false;
    }
  }

  private markSeen(): void {
    try {
      sessionStorage.setItem(SEEN_KEY, '1');
    } catch {
      // sem armazenamento: a abertura so aparece de novo ao recarregar
    }
  }

  private prefersReducedMotion(): boolean {
    return typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
}
