import { Component, signal } from '@angular/core';

/** Credito de quem fez o site, no fim das paginas publicas. */
@Component({
  selector: 'app-made-by',
  template: `
    <a href="https://www.instagram.com/inv.devs" target="_blank" rel="noopener"
      aria-label="Site feito por VN Bueno Digital (abre o Instagram @inv.devs)"
      class="group inline-flex items-center gap-4 rounded-2xl border border-white/10 bg-white/[0.03] px-5 py-3 hover:border-cyan-400/40 hover:bg-white/[0.06] transition">
      @if (logoOk()) {
        <img src="/vn-bueno-digital.png" alt="" (error)="logoOk.set(false)"
          class="h-14 w-14 shrink-0 object-contain transition group-hover:scale-110" />
      }
      <span class="text-left leading-tight">
        <span class="block text-[11px] uppercase tracking-[0.2em] text-neutral-500">Site feito por</span>
        <span class="block font-display text-lg font-bold text-neutral-100 group-hover:text-white">VN Bueno Digital</span>
        <span class="block text-xs text-cyan-300/80 group-hover:text-cyan-200">&#64;inv.devs no Instagram →</span>
      </span>
    </a>
  `,
})
export class MadeBy {
  /** Sem o arquivo da logo, fica so o nome. */
  readonly logoOk = signal(true);
}
