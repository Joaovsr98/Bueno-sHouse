import { Component, signal } from '@angular/core';

/** Credito de quem fez o site, no fim das paginas publicas. */
@Component({
  selector: 'app-made-by',
  template: `
    <a id="credito-digital" href="https://digital.vnbueno.com.br/" target="_blank" rel="noopener"
      aria-label="Site feito por VN Bueno Digital (abre o site oficial)"
      class="group inline-flex w-full min-w-0 max-w-[24rem] items-center justify-center gap-3 rounded-2xl border border-white/10 bg-white/[0.03] px-3 py-3 sm:px-5 hover:border-cyan-400/40 hover:bg-white/[0.06] transition">
      @if (logoOk()) {
        <img src="/vn-bueno-digital.png" alt="" (error)="logoOk.set(false)"
          class="h-12 w-12 sm:h-14 sm:w-14 shrink-0 object-contain transition group-hover:scale-110" />
      }
      <span class="min-w-0 break-words text-left leading-tight">
        <span class="block text-[11px] uppercase tracking-[0.2em] text-neutral-500">Site feito por</span>
        <span class="block font-display text-base font-bold sm:text-lg text-neutral-100 group-hover:text-white">VN Bueno Digital</span>
        <span class="block text-sm font-semibold bg-linear-to-r from-cyan-300 via-blue-400 to-violet-400 bg-clip-text text-transparent">Seu presente do futuro.</span>
        <span class="block text-xs text-cyan-300/80 group-hover:text-cyan-200">digital.vnbueno.com.br →</span>
      </span>
    </a>
  `,
})
export class MadeBy {
  /** Sem o arquivo da logo, fica so o nome. */
  readonly logoOk = signal(true);
}
