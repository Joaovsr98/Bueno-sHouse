import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { defaultRouteForRole } from '../../core/routing';
import { Logo } from '../../shared/logo';
import { Icon } from '../../shared/icon';
import { Backdrop } from '../../shared/backdrop';

/**
 * Pagina inicial: separa as duas entradas do sistema - o cliente (cardapio,
 * carrinho, pedidos) e a equipe do restaurante (login do sistema de gestao).
 * Quem ja esta logado segue direto para a propria area.
 */
@Component({
  selector: 'app-home',
  imports: [RouterLink, Logo, Icon, Backdrop],
  template: `
    <div class="relative min-h-screen overflow-hidden flex flex-col">
      <app-backdrop align="center" />

      <header class="relative px-6 sm:px-10 py-6 reveal">
        <app-logo [size]="44" />
      </header>

      <main class="relative flex-1 flex flex-col items-center justify-center px-6 pb-16">
        <div class="text-center max-w-4xl">
          <p class="eyebrow reveal reveal-1">Bueno's House · Uma experiência completa</p>
          <h1 class="mt-5 text-5xl sm:text-7xl font-bold uppercase leading-[0.95] reveal reveal-2">
            Por trás de cada pedido,<br /><span class="text-ember">uma experiência.</span>
          </h1>
          <p class="mt-6 text-lg text-neutral-400 reveal reveal-3">
            Tecnologia para quem faz acontecer. Simplicidade para quem quer aproveitar.
          </p>
          <p class="mt-12 font-display text-2xl sm:text-3xl font-bold uppercase reveal reveal-3">O que você <span class="text-ember">quer?</span></p>
        </div>

        <div class="mt-5 w-full max-w-4xl grid grid-cols-1 md:grid-cols-2 gap-5 reveal reveal-3">
          <a [routerLink]="customerLink()" [queryParams]="isCustomer() ? null : { returnUrl: '/app/cardapio' }" class="door group">
            <span class="door-icon"><app-icon name="cart" [size]="26" /></span>
            <span class="eyebrow-plain text-[0.62rem]">Para você</span>
            <span class="block font-display text-2xl xl:text-[1.7rem] font-bold uppercase mt-2 leading-tight">Quero matar minha fome</span>
            <span class="block text-sm text-neutral-400 mt-3 leading-relaxed">
              Escolha no cardápio, peça do seu jeito e acompanhe cada etapa até o lanche chegar na sua porta.
            </span>
            <span class="door-cta">{{ isCustomer() ? 'Continuar pedindo' : 'Entrar ou criar conta' }} <app-icon name="arrow" [size]="16" /></span>
          </a>

          <a [routerLink]="staffLink()" [queryParams]="isStaff() ? null : { lado: 'equipe' }" class="door group">
            <span class="door-icon"><app-icon name="chef" [size]="26" /></span>
            <span class="eyebrow-plain text-[0.62rem]">Para a equipe</span>
            <span class="block font-display text-2xl xl:text-[1.7rem] font-bold uppercase mt-2 leading-tight">Bora acender a chapa</span>
            <span class="block text-sm text-neutral-400 mt-3 leading-relaxed">
              Pedidos, cozinha, caixa, estoque e dashboard: tudo o que a equipe precisa para o serviço rodar.
            </span>
            <span class="door-cta">{{ isStaff() ? 'Voltar ao sistema' : 'Entrar no sistema' }} <app-icon name="arrow" [size]="16" /></span>
          </a>
        </div>

        <p class="mt-10 text-sm text-neutral-500 reveal reveal-4">
          Ainda não tem conta? <a routerLink="/registrar" class="text-brand-400 hover:text-brand-300">Cadastre-se para pedir</a>
        </p>
      </main>
    </div>
  `,
  styles: [`
    .door {
      position: relative; display: block; padding: 2rem; border-radius: 1.5rem; overflow: hidden;
      background: linear-gradient(180deg, rgba(255,255,255,.045), rgba(255,255,255,.015));
      border: 1px solid rgba(255,255,255,.09);
      transition: border-color .25s ease, transform .25s ease, background .25s ease, box-shadow .25s ease;
    }
    .door::before {
      content: ""; position: absolute; left: 0; top: 2rem; bottom: 2rem; width: 3px; border-radius: 3px;
      background: linear-gradient(180deg, #ff6a1a, #f5b544); opacity: 0; transition: opacity .25s ease;
    }
    .door:hover, .door:focus-visible {
      border-color: rgba(255,106,26,.5); transform: translateY(-3px);
      background: linear-gradient(180deg, rgba(255,106,26,.09), rgba(255,255,255,.02));
      box-shadow: 0 30px 60px -30px rgba(255,106,26,.45);
    }
    .door:hover::before, .door:focus-visible::before { opacity: 1; }
    .door-icon {
      display: grid; place-items: center; width: 3.25rem; height: 3.25rem; margin-bottom: 1.5rem;
      border-radius: 1rem; color: #ff8c3a; background: rgba(255,106,26,.1); border: 1px solid rgba(255,106,26,.25);
    }
    .door-cta {
      display: inline-flex; align-items: center; gap: .5rem; margin-top: 1.75rem;
      font-weight: 600; font-size: .9rem; color: #ff8c3a; transition: gap .2s ease;
    }
    .door:hover .door-cta { gap: .8rem; }
  `],
})
export class Home {
  private readonly auth = inject(AuthService);
  private readonly profile = computed(() => this.auth.user()?.profileName);

  readonly isCustomer = computed(() => this.profile() === 'CLIENTE');
  readonly isStaff = computed(() => !!this.profile() && this.profile() !== 'CLIENTE');
  /** Cliente logado vai direto ao cardapio; os demais entram ou criam conta e voltam para ele. */
  readonly customerLink = computed(() =>
    this.isCustomer() ? '/app/cardapio' : '/login',
  );
  readonly staffLink = computed(() => (this.isStaff() ? defaultRouteForRole(this.profile()) : '/login'));
}
