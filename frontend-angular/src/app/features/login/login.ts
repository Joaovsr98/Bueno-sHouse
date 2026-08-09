import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LoginResponse } from '../../core/models';
import { defaultRouteForRole } from '../../core/routing';

const DEMO_PASSWORD = 'admin123';

const QUICK_ACCESS = [
  { email: 'admin@demo.local', label: 'Administrador', icon: '🛡️' },
  { email: 'gerente@demo.local', label: 'Gerente', icon: '📊' },
  { email: 'garcom@demo.local', label: 'Garçom', icon: '🍽️' },
  { email: 'cozinha@demo.local', label: 'Cozinha', icon: '👨‍🍳' },
  { email: 'caixa@demo.local', label: 'Caixa', icon: '💳' },
  { email: 'motoboy@demo.local', label: 'Motoboy', icon: '🛵' },
];

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
})
export class Login {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly quickAccess = QUICK_ACCESS;
  email = 'admin@demo.local';
  password = '';
  error = signal<string | null>(null);
  loading = signal(false);

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
    this.api.post<LoginResponse>('/auth/login', { email, password }).subscribe({
      next: (data) => {
        this.auth.login(data);
        this.router.navigateByUrl(defaultRouteForRole(data.user.profileName));
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Erro ao entrar'));
        this.loading.set(false);
      },
    });
  }
}
