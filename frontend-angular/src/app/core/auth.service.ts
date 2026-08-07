import { Injectable, signal, computed } from '@angular/core';
import { LoggedUser, LoginResponse } from './models';

const TOKEN_KEY = 'restaurante:token';
const USER_KEY = 'restaurante:user';
const UNIT_KEY = 'restaurante:unitId';

/**
 * Sessao persistida em localStorage (token, usuario, unidade selecionada),
 * exposta por signals. Equivalente ao AuthContext do frontend React.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly _user = signal<LoggedUser | null>(readUser());
  private readonly _unitId = signal<number | null>(readUnitId());

  readonly token = this._token.asReadonly();
  readonly user = this._user.asReadonly();
  readonly unitId = this._unitId.asReadonly();
  readonly isLoggedIn = computed(() => this._token() !== null);

  login(data: LoginResponse): void {
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify(data.user));
    this._token.set(data.token);
    this._user.set(data.user);
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(UNIT_KEY);
    this._token.set(null);
    this._user.set(null);
    this._unitId.set(null);
  }

  setUnitId(id: number): void {
    localStorage.setItem(UNIT_KEY, String(id));
    this._unitId.set(id);
  }

  getToken(): string | null {
    return this._token();
  }
}

function readUser(): LoggedUser | null {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as LoggedUser) : null;
}

function readUnitId(): number | null {
  const raw = localStorage.getItem(UNIT_KEY);
  return raw ? Number(raw) : null;
}
