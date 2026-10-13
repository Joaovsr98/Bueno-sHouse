import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, apiErrorMessage } from '../../core/api.service';

interface UserRow {
  id: number;
  email: string;
  profileName: string;
  active: boolean;
}

const PROFILES = ['ADMINISTRADOR', 'GERENTE', 'CAIXA', 'GARCOM', 'COZINHA', 'MOTOBOY'];

const PROFILE_LABELS: Record<string, string> = {
  ADMINISTRADOR: 'Administrador',
  GERENTE: 'Gerente',
  CAIXA: 'Caixa',
  GARCOM: 'Garçom',
  COZINHA: 'Cozinha',
  MOTOBOY: 'Motoboy',
};

@Component({
  selector: 'app-users',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="mb-6">
        <h1 class="page-title">Usuários internos</h1>
        <p class="page-sub">Crie e gerencie o acesso da equipe (administrador, gerente, caixa, garçom, cozinha e motoboy).</p>
      </div>

      @if (error()) {
        <div class="alert-error mb-4">
          <span>{{ error() }}</span>
          <button (click)="error.set(null)" class="text-red-400">×</button>
        </div>
      }

      <section class="glass rounded-2xl p-5 mb-6">
        <h2 class="section-title">Novo usuário</h2>
        <form (ngSubmit)="create()" class="grid grid-cols-1 md:grid-cols-[1fr_1fr_12rem_auto] gap-3 items-end">
          <div>
            <label class="field-label" for="u-email">E-mail</label>
            <input id="u-email" class="field" type="email" name="email" [(ngModel)]="email" placeholder="nome@restaurante.com" required />
          </div>
          <div>
            <label class="field-label" for="u-pass">Senha</label>
            <input id="u-pass" class="field" type="password" name="password" [(ngModel)]="password" placeholder="Mínimo 8 caracteres" minlength="8" required />
          </div>
          <div>
            <label class="field-label" for="u-prof">Perfil</label>
            <select id="u-prof" class="field" name="profile" [(ngModel)]="profileName">
              @for (p of profiles; track p) { <option [value]="p">{{ profileLabels[p] }}</option> }
            </select>
          </div>
          <button type="submit" [disabled]="saving()" class="btn-brand rounded-xl px-4 py-2 text-sm">
            {{ saving() ? 'Criando...' : 'Criar usuário' }}
          </button>
        </form>
      </section>

      <section class="glass rounded-2xl overflow-hidden">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>E-mail</th><th>Perfil</th><th>Situação</th><th class="num">Ações</th></tr>
            </thead>
            <tbody>
              @for (u of users(); track u.id) {
                <tr>
                  <td>{{ u.email }}</td>
                  <td>
                    <select class="field !w-44 !py-1" [ngModel]="u.profileName" (ngModelChange)="update(u, { profileName: $event })">
                      @for (p of profiles; track p) { <option [value]="p">{{ profileLabels[p] }}</option> }
                    </select>
                  </td>
                  <td>
                    <span class="badge" [class]="u.active ? 'badge-green' : 'badge-gray'">{{ u.active ? 'Ativo' : 'Inativo' }}</span>
                  </td>
                  <td class="num">
                    <div class="inline-flex gap-2">
                      <button class="btn-ghost" (click)="resetPassword(u)">Redefinir senha</button>
                      <button class="btn-ghost" [class.btn-danger]="u.active" (click)="update(u, { active: !u.active })">
                        {{ u.active ? 'Desativar' : 'Ativar' }}
                      </button>
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="empty">Nenhum usuário interno cadastrado.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
})
export class Users implements OnInit {
  private readonly api = inject(ApiService);
  readonly profiles = PROFILES;
  readonly profileLabels: Record<string, string> = PROFILE_LABELS;
  saving = signal(false);
  users = signal<UserRow[]>([]);
  error = signal<string | null>(null);
  email = '';
  password = '';
  profileName = 'GARCOM';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.get<UserRow[]>('/users').subscribe({
      next: (u) => this.users.set(u),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar usuários')),
    });
  }

  create(): void {
    this.error.set(null);
    this.saving.set(true);
    this.api.post('/users', { email: this.email, password: this.password, profileName: this.profileName }).subscribe({
      next: () => {
        this.saving.set(false);
        this.email = '';
        this.password = '';
        this.load();
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(e, 'Erro ao criar usuário'));
      },
    });
  }

  update(u: UserRow, body: { profileName?: string; active?: boolean; newPassword?: string }): void {
    this.error.set(null);
    this.api.patch(`/users/${u.id}`, body).subscribe({
      next: () => this.load(),
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'Erro ao atualizar usuário'));
        this.load();
      },
    });
  }

  resetPassword(u: UserRow): void {
    const newPassword = window.prompt(`Nova senha para ${u.email} (mín. 8 caracteres):`);
    if (newPassword) this.update(u, { newPassword });
  }
}
