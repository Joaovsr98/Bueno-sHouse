import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { CartService } from '../../core/cart.service';
import { ToastService } from '../../core/toast.service';
import { ProductResponse } from '../../core/models';
import { currency } from '../../core/format';
import { Icon } from '../../shared/icon';

/**
 * Pagina de detalhe do prato (RF-002): foto, descricao, modo de preparo (RF-014),
 * tempo de preparo, quantidade e observacao antes de ir para o carrinho.
 * Publica como o cardapio; o carrinho e que exige login.
 */
@Component({
  selector: 'app-customer-product',
  imports: [FormsModule, RouterLink, Icon],
  template: `
    <a routerLink="/app/cardapio" class="inline-flex items-center gap-2 text-sm text-neutral-400 hover:text-neutral-100 mb-6">
      <span class="rotate-180 inline-flex"><app-icon name="arrow" [size]="16" /></span> Voltar ao cardápio
    </a>

    @if (error()) {
      <div class="alert-error">{{ error() }}</div>
    } @else if (!product()) {
      <p class="text-sm text-neutral-500">Carregando prato...</p>
    } @else {
      @let p = product()!;
      <article class="grid grid-cols-1 md:grid-cols-[1.05fr_1fr] gap-8 items-start reveal">
        <div class="relative rounded-3xl overflow-hidden border border-white/10 bg-ink-900 aspect-[4/3]">
          @if (p.imageUrl) {
            <img [src]="p.imageUrl" [alt]="p.name" class="absolute inset-0 w-full h-full object-cover" />
          } @else {
            <div class="absolute inset-0 grid place-items-center text-brand-400/70">
              <app-icon name="utensils" [size]="64" [stroke]="1.2" />
            </div>
          }
          <div class="absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-black/60 to-transparent"></div>
          <span class="absolute left-4 bottom-4 eyebrow-plain text-[0.62rem] text-neutral-200">{{ p.categoryName }}</span>
        </div>

        <div class="space-y-6">
          <div>
            <h1 class="text-4xl sm:text-5xl font-bold uppercase leading-[0.95]">{{ p.name }}</h1>
            <div class="mt-4 flex flex-wrap items-center gap-3 text-sm">
              <span class="text-2xl font-display font-bold text-brand-300">{{ currency(p.basePrice) }}</span>
              @if (p.prepTimeMinutes) {
                <span class="inline-flex items-center gap-1.5 rounded-full border border-white/10 px-3 py-1 text-neutral-300">
                  <app-icon name="clock" [size]="14" /> Pronto em ~{{ p.prepTimeMinutes }} min
                </span>
              }
            </div>
          </div>

          @if (p.description) {
            <p class="text-neutral-300 leading-relaxed">{{ p.description }}</p>
          }

          @if (p.preparationMethod) {
            <section class="glass rounded-2xl p-5">
              <h2 class="eyebrow-plain text-[0.65rem] flex items-center gap-2 text-brand-400">
                <app-icon name="flame" [size]="14" /> Modo de preparo
              </h2>
              <p class="mt-3 text-sm text-neutral-300 leading-relaxed">{{ p.preparationMethod }}</p>
            </section>
          }

          @if (!p.available) {
            <div class="alert-error">Este prato está indisponível no momento.</div>
          } @else {
            <div class="space-y-4">
              <label class="block space-y-1.5">
                <span class="field-label">Observações (opcional)</span>
                <input name="notes" [(ngModel)]="notes" placeholder="Ex.: sem cebola, ponto da carne..." maxlength="200" class="field py-3" />
              </label>
              <div class="flex items-center gap-3">
                <div class="flex items-center gap-2 rounded-xl border border-white/10 p-1">
                  <button (click)="changeQty(-1)" class="w-9 h-9 rounded-lg hover:bg-white/10" aria-label="Diminuir quantidade">−</button>
                  <span class="w-7 text-center font-semibold" aria-live="polite">{{ qty() }}</span>
                  <button (click)="changeQty(1)" class="w-9 h-9 rounded-lg hover:bg-white/10" aria-label="Aumentar quantidade">+</button>
                </div>
                <button (click)="add()" class="flex-1 btn-brand rounded-xl py-3 text-sm inline-flex items-center justify-center gap-2">
                  <app-icon name="cart" [size]="16" /> Adicionar ({{ currency(p.basePrice * qty()) }})
                </button>
              </div>
            </div>
          }
        </div>
      </article>
    }
  `,
})
export class CustomerProduct {
  private readonly api = inject(ApiService);
  private readonly cart = inject(CartService);
  private readonly toast = inject(ToastService);

  private readonly route = inject(ActivatedRoute);

  readonly currency = currency;
  readonly product = signal<ProductResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly qty = signal(1);
  notes = '';

  constructor() {
    // /app/cardapio/:productId (paramMap: trocar de prato sem sair da tela recarrega)
    this.route.paramMap.subscribe((params) => this.load(params.get('productId') ?? ''));
  }

  private async load(id: string): Promise<void> {
    this.product.set(null);
    this.error.set(null);
    try {
      this.product.set(await firstValueFrom(this.api.get<ProductResponse>(`/products/${id}`)));
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Prato não encontrado'));
    }
  }

  changeQty(delta: number): void {
    this.qty.update((q) => Math.max(1, q + delta));
  }

  add(): void {
    const p = this.product();
    if (!p) return;
    this.cart.add(p, this.qty(), this.notes.trim());
    this.toast.success(`${this.qty()}x ${p.name} adicionado ao carrinho`);
    this.qty.set(1);
    this.notes = '';
  }
}
