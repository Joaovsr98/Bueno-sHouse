import { Component, inject, signal } from '@angular/core';
import { NavigationCancel, NavigationEnd, NavigationError, NavigationStart, Router, RouterOutlet } from '@angular/router';
import { ToastService } from './core/toast.service';
import { Intro } from './shared/intro';
import { Burners } from './shared/burners';

/** Abaixo disso a troca de tela e instantanea e nao vale piscar o indicador. */
const ROUTE_LOADING_DELAY_MS = 250;

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Intro, Burners],
  template: `
    <app-intro />
    <router-outlet />
    @if (routeLoading()) {
      <div class="fixed inset-0 z-[90] grid place-items-center bg-ink-950/70 backdrop-blur-sm animate-rise">
        <app-burners [size]="18" />
      </div>
    }
    <div class="fixed bottom-4 right-4 z-50 flex flex-col gap-2 w-[min(22rem,calc(100vw-2rem))]" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div
          role="status"
          class="glass rounded-xl px-4 py-3 text-sm flex items-start gap-3 animate-rise border"
          [class]="t.kind === 'success' ? 'border-green-700/60 text-green-200' : 'border-red-700/60 text-red-200'"
        >
          <span>{{ t.kind === 'success' ? '✓' : '!' }}</span>
          <span class="flex-1">{{ t.message }}</span>
          <button (click)="toast.dismiss(t.id)" class="text-neutral-500 hover:text-neutral-200" aria-label="Fechar">×</button>
        </div>
      }
    </div>
  `,
})
export class App {
  readonly toast = inject(ToastService);
  /** Tela carregando (pacote da rota ainda baixando): mostra as bocas acendendo. */
  readonly routeLoading = signal(false);
  private pending?: ReturnType<typeof setTimeout>;

  constructor() {
    inject(Router).events.subscribe((e) => {
      if (e instanceof NavigationStart) {
        clearTimeout(this.pending);
        this.pending = setTimeout(() => this.routeLoading.set(true), ROUTE_LOADING_DELAY_MS);
      } else if (e instanceof NavigationEnd || e instanceof NavigationCancel || e instanceof NavigationError) {
        clearTimeout(this.pending);
        this.routeLoading.set(false);
      }
    });
  }
}
