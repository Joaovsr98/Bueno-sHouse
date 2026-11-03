import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

interface ResetStatus {
  running: boolean;
  finishedAt: string | null;
  error: string | null;
}

/**
 * Restaurar a demonstracao (so administrador): apaga o que foi criado nos testes e volta
 * cardapio, estoque, historico e usuarios de exemplo ao estado inicial. O servidor recria o
 * banco em segundo plano (~1 min); a tela acompanha sem login (nao ha usuarios durante a
 * restauracao) e no fim pede para entrar de novo.
 */
@Component({
  selector: 'app-demo-reset',
  imports: [FormsModule],
  template: `
    <section class="glass rounded-2xl p-5 mt-6 border border-red-500/25">
      <h2 class="section-title">Dados de demonstração</h2>
      <p class="text-sm text-neutral-300">
        Restaurar apaga tudo o que foi criado nos testes (pedidos, clientes, usuários, compras) e volta cardápio,
        estoque, histórico de vendas e usuários de exemplo ao estado inicial.
      </p>
      <p class="text-xs text-neutral-500 mt-1">Leva cerca de 1 minuto. Nesse tempo o sistema fica fora do ar e todos precisam entrar de novo.</p>

      @switch (phase()) {
        @case ('running') {
          <p class="mt-4 text-sm text-gold-400" role="status">Restaurando a demonstração... {{ seconds() }} s</p>
        }
        @case ('done') {
          <p class="mt-4 text-sm text-green-300" role="status">Demonstração restaurada. Entre de novo.</p>
        }
        @default {
          @if (error()) {
            <div class="alert-error mt-4">
              <span>{{ error() }}</span>
              <button (click)="error.set(null)" class="text-red-400">×</button>
            </div>
          }
          <div class="mt-4 flex flex-wrap items-center gap-2">
            <label class="sr-only" for="demo-confirm">Digite RESTAURAR para confirmar</label>
            <input id="demo-confirm" class="field max-w-52" name="confirm" [(ngModel)]="confirm"
              placeholder="Digite RESTAURAR" autocomplete="off" />
            <button (click)="restore()" [disabled]="confirm !== 'RESTAURAR'"
              class="rounded-xl px-4 py-2 text-sm font-semibold bg-red-600/80 text-white hover:bg-red-600 transition disabled:opacity-40 disabled:cursor-not-allowed">
              Restaurar demonstração
            </button>
          </div>
        }
      }
    </section>
  `,
})
export class DemoReset {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private destroyed = false;

  confirm = '';
  phase = signal<'idle' | 'running' | 'done'>('idle');
  seconds = signal(0);
  error = signal<string | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => (this.destroyed = true));
  }

  restore(): void {
    this.api.post<{ message: string }>('/admin/demo/reset', { confirm: this.confirm }).subscribe({
      next: () => {
        this.phase.set('running');
        void this.watch();
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao restaurar a demonstração')),
    });
  }

  /** Acompanha pelo status publico (sem token: durante a restauracao nao ha usuarios). */
  private async watch(): Promise<void> {
    const started = Date.now();
    while (!this.destroyed && Date.now() - started < 5 * 60_000) {
      await new Promise((r) => setTimeout(r, 3000));
      this.seconds.set(Math.round((Date.now() - started) / 1000));
      try {
        const res = await fetch('/api/demo/reset-status');
        if (!res.ok) continue;
        const status: ResetStatus = await res.json();
        if (status.running) continue;
        if (status.error) {
          this.phase.set('idle');
          this.error.set(`A restauração falhou: ${status.error}`);
          return;
        }
        this.phase.set('done');
        setTimeout(() => {
          this.auth.logout();
          void this.router.navigateByUrl('/login');
        }, 1500);
        return;
      } catch {
        // servidor ocupado recriando o banco: tenta de novo
      }
    }
    if (!this.destroyed) {
      this.phase.set('idle');
      this.error.set('A restauração está demorando mais que o normal. Recarregue a página em alguns minutos.');
    }
  }
}
