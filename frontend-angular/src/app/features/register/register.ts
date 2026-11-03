import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { UnitsService } from '../../core/units.service';
import { AuthService } from '../../core/auth.service';
import { CustomerResponse, DeliveryZoneResponse, LoginResponse, ProductResponse } from '../../core/models';
import { defaultRouteForRole } from '../../core/routing';
import { currency } from '../../core/format';
import { Logo } from '../../shared/logo';
import { Icon } from '../../shared/icon';
import { groupZones } from '../../core/zones';
import { CepLookup } from '../../core/cep';

/**
 * Cadastro publico de Cliente (RF-004): dados pessoais + endereco de entrega.
 * Sempre cria conta com perfil CLIENTE (RF-039). Se veio do carrinho, volta
 * para o checkout (RF-005).
 */
@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink, Logo, Icon],
  templateUrl: './register.html',
})
export class Register {
  private readonly api = inject(ApiService);
  private readonly unitsService = inject(UnitsService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly currency = currency;
  readonly returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');

  fullName = '';
  email = '';
  password = '';
  phone = '';
  addr = { street: '', number: '', complement: '', neighborhood: '', city: 'São Paulo', state: 'SP', zipCode: '' };
  zones = signal<DeliveryZoneResponse[]>([]);
  readonly zoneGroups = computed(() => groupZones(this.zones()));
  readonly zoneCount = computed(() => this.zones().length);

  /** Zona do bairro escolhido (o select usa ngModel simples, entao lemos o valor atual). */
  selectedZone(): DeliveryZoneResponse | undefined {
    return this.zones().find((z) => z.neighborhood === this.addr.neighborhood);
  }
  error = signal<string | null>(null);
  loading = signal(false);
  /** CEP: mascara, ViaCEP e checagem de cidade/bairro atendido. */
  readonly cep = new CepLookup();
  /** Destaques do cardapio, mostrados quando o CEP e so para retirada (para nao perder o interesse). */
  readonly teaser = signal<ProductResponse[]>([]);

  constructor() {
    this.loadZones();
  }

  private async loadZones(): Promise<void> {
    try {
      const units = await this.unitsService.list();
      const unitId = units[0]?.id ?? 1;
      const [zones, products] = await Promise.all([
        firstValueFrom(this.api.get<DeliveryZoneResponse[]>(`/delivery-zones?unitId=${unitId}`)),
        firstValueFrom(this.api.get<ProductResponse[]>(`/products?unitId=${unitId}`)),
      ]);
      this.zones.set(zones.filter((z) => z.active));
      // Com foto primeiro: e o que da fome.
      const available = products.filter((p) => p.available);
      this.teaser.set([...available.filter((p) => p.imageUrl), ...available.filter((p) => !p.imageUrl)].slice(0, 3));
    } catch {
      this.zones.set([]);
    }
  }

  async onCep(input: HTMLInputElement): Promise<void> {
    const lookup = this.cep.onInput(input.value, this.addr, this.zones());
    input.value = this.addr.zipCode; // mascara aplicada na hora, mesmo se a pessoa digitar letra
    await lookup;
  }

  async submit(): Promise<void> {
    const a = this.addr;
    // Fora da area de entrega: a conta e criada sem endereco e o pedido sai como retirada.
    const pickupOnly = this.cep.pickupOnly();
    if (!pickupOnly) {
      if (!a.street || !a.number || !a.neighborhood || !a.city || !a.state || !a.zipCode) {
        this.error.set('Preencha o endereço de entrega: CEP, rua, número e bairro');
        return;
      }
      const cepError = this.cep.validate(a);
      if (cepError) {
        this.error.set(cepError);
        return;
      }
    }
    this.error.set(null);
    this.loading.set(true);
    try {
      const data = await firstValueFrom(
        this.api.post<LoginResponse>('/auth/register', {
          fullName: this.fullName,
          email: this.email,
          password: this.password,
          phone: this.phone,
        }),
      );
      this.auth.login(data);
      if (!pickupOnly) {
        const me = await firstValueFrom(this.api.get<CustomerResponse>('/customers/me'));
        await firstValueFrom(
          this.api.post(`/customers/${me.id}/addresses`, { ...a, label: 'Casa', isDefault: true }),
        );
      }
      this.router.navigateByUrl(this.returnUrl || defaultRouteForRole(data.user.profileName));
    } catch (err) {
      // Conta criada mas endereco recusado: segue logado, endereco pode ser feito no checkout.
      if (this.auth.isLoggedIn()) {
        this.router.navigateByUrl(this.returnUrl || '/app/carrinho');
        return;
      }
      this.error.set(apiErrorMessage(err, 'Erro ao criar conta'));
      this.loading.set(false);
    }
  }
}
