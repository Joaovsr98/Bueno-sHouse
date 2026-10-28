import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, retry, throwError, timer } from 'rxjs';
import { AuthService } from './auth.service';

/** Gateway indisponivel: o backend gratuito do Render esta acordando ou reiniciando. */
const WAKING_STATUSES = [502, 503, 504];
const WAKE_RETRIES = 6;
const WAKE_DELAY_MS = 4000;

/**
 * Anexa o token JWT (Bearer) no header Authorization de toda requisicao para
 * /api. Se a API responder 401 (token expirado/invalido), encerra a sessao e
 * volta para o login guardando a tela atual em ?returnUrl (RF-042).
 *
 * Enquanto o backend acorda (502/503/504), repete a chamada por ~25s. So repete
 * o que e seguro repetir: leituras (GET) e o login - nunca um POST que crie
 * pedido ou pagamento em dobro.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.getToken();
  if (token && req.url.startsWith('/api')) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }
  const safeToRetry = req.method === 'GET' || req.url === '/api/auth/login';
  return next(req).pipe(
    retry({
      count: safeToRetry ? WAKE_RETRIES : 0,
      delay: (err: unknown) =>
        err instanceof HttpErrorResponse && WAKING_STATUSES.includes(err.status)
          ? timer(WAKE_DELAY_MS)
          : throwError(() => err),
    }),
    catchError((err: unknown) => {
      const isLoginCall = req.url.startsWith('/api/auth/');
      if (err instanceof HttpErrorResponse && err.status === 401 && token && !isLoginCall) {
        auth.logout();
        router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => err);
    }),
  );
};
