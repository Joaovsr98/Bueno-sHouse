import { Component, signal } from '@angular/core';

/** Credito de quem fez o site, no fim das paginas publicas. */
@Component({
  selector: 'app-made-by',
  template: `
    <a href="https://www.instagram.com/inv.devs" target="_blank" rel="noopener"
      aria-label="Site feito por INV Devs (abre o Instagram)"
      class="group inline-flex items-center gap-2 text-xs text-neutral-500 hover:text-neutral-300 transition">
      Site feito por
      @if (logoOk()) {
        <img src="/inv-devs.webp" alt="" class="h-8 w-8 rounded-full transition group-hover:scale-110" (error)="logoOk.set(false)" />
      }
      <span class="font-semibold text-neutral-300 group-hover:text-white">INV Devs</span>
    </a>
  `,
})
export class MadeBy {
  /** Sem o arquivo da logo, fica so o nome. */
  readonly logoOk = signal(true);
}
