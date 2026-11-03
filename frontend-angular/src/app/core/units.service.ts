import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { UnitResponse } from './models';

/**
 * Unidades do restaurante, buscadas uma vez e compartilhadas pelas telas do cliente
 * (o cardapio e o cartao "Onde estamos" pediam a mesma lista duas vezes).
 */
@Injectable({ providedIn: 'root' })
export class UnitsService {
  private readonly api = inject(ApiService);
  private cached: Promise<UnitResponse[]> | null = null;

  list(): Promise<UnitResponse[]> {
    this.cached ??= firstValueFrom(this.api.get<UnitResponse[]>('/units')).catch((e) => {
      this.cached = null; // falhou: a proxima tela tenta de novo
      throw e;
    });
    return this.cached;
  }
}
