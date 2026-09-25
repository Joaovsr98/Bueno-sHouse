import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { LoginResponse } from '../../core/models';
import { defaultRouteForRole } from '../../core/routing';

/** Cadastro publico de Cliente (RF-004). Sempre cria conta com perfil CLIENTE. */
@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
})
export class Register {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  fullName = '';
  email = '';
  password = '';
  phone = '';
  error = signal<string | null>(null);
  loading = signal(false);

  submit(): void {
    this.error.set(null);
    this.loading.set(true);
    this.api
      .post<LoginResponse>('/auth/register', {
        fullName: this.fullName,
        email: this.email,
        password: this.password,
        phone: this.phone,
      })
      .subscribe({
        next: (data) => {
          this.auth.login(data);
          this.router.navigateByUrl(defaultRouteForRole(data.user.profileName));
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err, 'Erro ao criar conta'));
          this.loading.set(false);
        },
      });
  }
}
