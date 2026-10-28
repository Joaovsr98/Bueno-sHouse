import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LoginResponse } from '../../core/models';
import { defaultRouteForRole } from '../../core/routing';
import { Logo } from '../../shared/logo';
import { Icon } from '../../shared/icon';
import { Backdrop } from '../../shared/backdrop';
import { Burners } from '../../shared/burners';

const DEMO_PASSWORD = 'admin123';

const QUICK_ACCESS = [
  { email: 'admin@demo.local', label: 'Administrador', icon: 'shield' },
  { email: 'gerente@demo.local', label: 'Gerente', icon: 'chart' },
  { email: 'garcom@demo.local', label: 'Garçom', icon: 'utensils' },
  { email: 'cozinha@demo.local', label: 'Cozinha', icon: 'chef' },
  { email: 'caixa@demo.local', label: 'Caixa', icon: 'card' },
  { email: 'motoboy@demo.local', label: 'Motoboy', icon: 'bike' },
];

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink, Logo, Icon, Backdrop, Burners],
  templateUrl: './login.html',
})
export class Login {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly quickAccess = QUICK_ACCESS;
  readonly returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
  /**
   * Por qual porta a pessoa chegou: "equipe" (Bora acender a chapa) mostra so os
   * perfis de funcionario e barra conta de cliente; "cliente" (Quero matar minha
   * fome, ou vindo de uma tela do cliente) mostra so entrar como cliente ou criar
   * conta. Sem porta (link direto para /login), mostra tudo.
   */
  readonly side: 'equipe' | 'cliente' | null =
    this.route.snapshot.queryParamMap.get('lado') === 'equipe'
      ? 'equipe'
      : this.route.snapshot.queryParamMap.get('lado') === 'cliente' || this.returnUrl?.startsWith('/app')
        ? 'cliente'
        : null;
  /** Login de demonstracao exigido pelo guia da disciplina (equipe). */
  email = this.side === 'cliente' ? '' : 'admin@email.com';
  password = '';
  error = signal<string | null>(null);
  loading = signal(false);
  showPassword = signal(false);
  showForgot = signal(false);
  /** Login demorando: o backend gratuito provavelmente esta acordando. */
  slow = signal(false);
  private slowTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // Acorda o backend (Render gratuito dorme sem uso) enquanto a pessoa digita.
    this.api.get('/health').subscribe({ error: () => undefined });
  }

  submit(): void {
    this.doLogin(this.email, this.password);
  }

  quickLogin(email: string): void {
    this.email = email;
    this.password = DEMO_PASSWORD;
    this.doLogin(email, DEMO_PASSWORD);
  }

  private doLogin(email: string, password: string): void {
    this.error.set(null);
    this.loading.set(true);
    this.slow.set(false);
    this.slowTimer = setTimeout(() => this.slow.set(true), 4000);
    this.api.post<LoginResponse>('/auth/login', { email, password }).subscribe({
      next: (data) => {
        this.stopSlowTimer();
        const isCustomer = data.user.profileName === 'CLIENTE';
        if (this.side === 'equipe' && isCustomer) {
          this.error.set('Esta entrada é só para a equipe. Clientes entram por "Matar minha fome".');
          this.loading.set(false);
          return;
        }
        this.auth.login(data);
        // Funcionario que entrou pela porta do cliente vai para a propria area.
        const returnUrl = this.side === 'cliente' && !isCustomer ? null : this.returnUrl;
        this.router.navigateByUrl(returnUrl || defaultRouteForRole(data.user.profileName));
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Erro ao entrar'));
        this.loading.set(false);
        this.stopSlowTimer();
      },
    });
  }

  private stopSlowTimer(): void {
    if (this.slowTimer) clearTimeout(this.slowTimer);
    this.slow.set(false);
  }
}
