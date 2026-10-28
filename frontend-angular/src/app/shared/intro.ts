import { Component, OnDestroy, signal } from '@angular/core';
import { Logo } from './logo';

const SEEN_KEY = 'bueno.intro.seen';
const BURNERS = 3;

/**
 * Abertura "acendendo a chapa" (inspirada na largada do Paddock 22): as bocas
 * acendem uma a uma e o servico abre. Toda vez que se entra pela pagina inicial,
 * com botao de pular (nas outras telas, recarregar nao repete a abertura);
 * quem prefere menos movimento (prefers-reduced-motion) nao ve a animacao.
 */
@Component({
  selector: 'app-intro',
  imports: [Logo],
  template: `
    @if (visible()) {
      <div class="fixed inset-0 z-[100] grid place-items-center bg-ink-950 transition-opacity duration-500"
        [class.opacity-0]="leaving()" role="presentation">
        <div class="flex flex-col items-center gap-8">
          <app-logo [size]="64" [wordmark]="false" />
          <div class="flex gap-5">
            @for (b of burners; track b) {
              <span class="burner" [class.on]="lit() > b"></span>
            }
          </div>
          <p class="eyebrow-plain text-[0.7rem]" [class.text-brand-400]="lit() >= burners.length">
            {{ lit() >= burners.length ? 'Serviço aberto' : 'Acendendo a chapa' }}
          </p>
        </div>
        <button (click)="finish()" class="absolute bottom-6 right-6 eyebrow-plain text-[0.65rem] hover:text-neutral-200">Pular →</button>
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
  `],
})
export class Intro implements OnDestroy {
  readonly burners = Array.from({ length: BURNERS }, (_, i) => i);
  readonly visible = signal(false);
  readonly leaving = signal(false);
  readonly lit = signal(0);
  private timers: ReturnType<typeof setTimeout>[] = [];

  constructor() {
    if ((!this.onHome() && this.alreadySeen()) || this.prefersReducedMotion()) return;
    this.visible.set(true);
    this.burners.forEach((i) => this.timers.push(setTimeout(() => this.lit.set(i + 1), 350 + i * 380)));
    this.timers.push(setTimeout(() => this.finish(), 350 + BURNERS * 380 + 700));
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
