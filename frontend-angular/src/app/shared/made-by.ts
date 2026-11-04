import { Component, signal } from '@angular/core';

/** Credito de quem fez o site, no fim das paginas publicas. */
@Component({
  selector: 'app-made-by',
  template: `
    <p class="inline-flex items-center gap-2 text-xs text-neutral-500">
      Site feito por
      @if (logoOk()) {
        <img src="/inv-devs.webp" alt="INV Devs" class="h-8 w-8 rounded-full" (error)="logoOk.set(false)" />
      }
      <span class="font-semibold text-neutral-300">INV Devs</span>
    </p>
  `,
})
export class MadeBy {
  /** Sem o arquivo da logo, fica so o nome. */
  readonly logoOk = signal(true);
}
