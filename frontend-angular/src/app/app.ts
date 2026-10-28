import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastService } from './core/toast.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <router-outlet />
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
}
