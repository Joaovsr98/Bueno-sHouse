import { Injectable, signal } from '@angular/core';

/**
 * Liga a pagina inicial a abertura com a escolha "O que voce quer?". A propria pagina
 * inicial pede a abertura sempre que aparece; assim nao importa como a pessoa chegou
 * ate ela (link, botao Voltar do navegador, endereco inexistente redirecionado, cache).
 */
@Injectable({ providedIn: 'root' })
export class IntroService {
  /** Cada incremento pede a abertura com a escolha. */
  readonly requests = signal(0);
  /** A pessoa escolheu ver a pagina inicial: nao reabre ate ela sair da pagina. */
  stayOnHome = false;

  show(): void {
    this.requests.update((n) => n + 1);
  }
}
