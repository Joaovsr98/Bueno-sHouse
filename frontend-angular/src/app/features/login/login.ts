import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LoginResponse } from '../../core/models';
import { defaultRouteForRole } from '../../core/routing';
import { Logo } from '../../shared/logo';
import { Icon } from '../../shared/icon';

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
  imports: [FormsModule, RouterLink, Logo, Icon],
  templateUrl: './login.html',
})
export class Login {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly quickAccess = QUICK_ACCESS;
  /** Login de demonstracao exigido pelo guia da disciplina. */
  email = 'admin@email.com';
  readonly returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
  password = '';
  error = signal<string | null>(null);
  loading = signal(false);
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
        this.auth.login(data);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
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
