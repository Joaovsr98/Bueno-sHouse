import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Client de API fino sobre o HttpClient. O prefixo /api e o token (via
 * interceptor) sao aplicados automaticamente. Equivalente ao lib/api.ts do
 * frontend React.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  get<T>(path: string): Observable<T> {
    return this.http.get<T>(`/api${path}`);
  }

  post<T>(path: string, body?: unknown): Observable<T> {
    return this.http.post<T>(`/api${path}`, body ?? {});
  }

  put<T>(path: string, body?: unknown): Observable<T> {
    return this.http.put<T>(`/api${path}`, body ?? {});
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(`/api${path}`);
  }
}

/** Extrai a mensagem de erro do ApiError do backend, com fallback. */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const e = err as { error?: { message?: string }; status?: number };
  return e?.error?.message || (e?.status ? `Erro HTTP ${e.status}` : fallback);
}
