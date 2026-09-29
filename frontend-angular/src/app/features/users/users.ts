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

@Component({
  selector: 'app-users',
  imports: [FormsModule],
  template: `
    <h1>Usuários internos</h1>
    @if (error()) { <p>{{ error() }}</p> }

    <h2>Novo usuário</h2>
    <form (ngSubmit)="create()">
      <input name="email" [(ngModel)]="email" placeholder="E-mail" required />
      <input name="password" type="password" [(ngModel)]="password" placeholder="Senha (mín. 8)" required />
      <select name="profile" [(ngModel)]="profileName">
        @for (p of profiles; track p) { <option [value]="p">{{ p }}</option> }
      </select>
      <button type="submit">Criar</button>
    </form>

    <h2>Lista</h2>
    <table border="1">
      <tr><th>E-mail</th><th>Perfil</th><th>Ativo</th><th>Ações</th></tr>
      @for (u of users(); track u.id) {
        <tr>
          <td>{{ u.email }}</td>
          <td>
            <select [ngModel]="u.profileName" (ngModelChange)="update(u, { profileName: $event })">
              @for (p of profiles; track p) { <option [value]="p">{{ p }}</option> }
            </select>
          </td>
          <td>{{ u.active ? 'Sim' : 'Não' }}</td>
          <td>
            <button (click)="update(u, { active: !u.active })">{{ u.active ? 'Desativar' : 'Ativar' }}</button>
            <button (click)="resetPassword(u)">Redefinir senha</button>
          </td>
        </tr>
      }
    </table>
  `,
})
export class Users implements OnInit {
  private readonly api = inject(ApiService);
  readonly profiles = PROFILES;
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
    this.api.post('/users', { email: this.email, password: this.password, profileName: this.profileName }).subscribe({
      next: () => {
        this.email = '';
        this.password = '';
        this.load();
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao criar usuário')),
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
