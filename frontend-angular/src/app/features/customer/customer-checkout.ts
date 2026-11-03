import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { UnitsService } from '../../core/units.service';
import { CartService } from '../../core/cart.service';
import {
  CustomerResponse,
  CustomerAddressResponse,
  DeliveryZoneResponse,
  OrderResponse,
} from '../../core/models';
import { currency } from '../../core/format';
import { groupZones } from '../../core/zones';
import { CepLookup } from '../../core/cep';
import { RESTAURANT, directionsLink, distanceKm, formatDistance, geocode } from '../../core/location';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'app-customer-checkout',
  imports: [RouterLink, FormsModule, Icon],
  templateUrl: './customer-checkout.html',
})
export class CustomerCheckout {
  private readonly api = inject(ApiService);
  private readonly unitsService = inject(UnitsService);
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);

  readonly currency = currency;
  readonly lines = this.cart.lines;
  readonly subtotal = this.cart.subtotal;

  addresses = signal<CustomerAddressResponse[]>([]);
  selectedAddressId = signal<number | null>(null);
  error = signal<string | null>(null);
  placing = signal(false);
  customerId = signal<number | null>(null);
  showAddressForm = signal(false);
  savingAddress = signal(false);
  /** CEP: mascara, ViaCEP e checagem de cidade/bairro atendido. */
  readonly cep = new CepLookup();
  addr = { label: '', street: '', number: '', complement: '', neighborhood: '', city: 'São Paulo', state: 'SP', zipCode: '' };

  /**
   * RF-006: pagamento simulado (nenhuma cobranca real). A forma escolhida vai no pedido e,
   * quando a entrega e confirmada (ou a retirada finalizada), e lancada no caixa.
   */
  readonly paymentMethods = [
    { value: 'PIX', label: 'Pix' },
    { value: 'CARTAO_CREDITO', label: 'Cartão de crédito' },
    { value: 'CARTAO_DEBITO', label: 'Cartão de débito' },
    { value: 'DINHEIRO', label: 'Dinheiro' },
  ];
  paymentMethod = 'PIX';
  changeFor = '';
  orderNotes = '';

  /** Bairros atendidos: o backend so aceita pedido se o bairro bater com uma zona. */
  zones = signal<DeliveryZoneResponse[]>([]);
  readonly zoneGroups = computed(() => groupZones(this.zones()));

  /** Distancia do endereco escolhido ate a cozinha, com link da rota no Google Maps. */
  distance = signal<{ text: string; route: string } | null>(null);

  /** Entrega em casa ou retirada no balcao (sem taxa, para quem esta fora da area). */
  mode = signal<'DELIVERY' | 'RETIRADA'>('DELIVERY');
  readonly pickupRoute = directionsLink(RESTAURANT.fallbackAddress);

  canPlace(): boolean {
    if (this.placing() || this.lines().length === 0) return false;
    if (this.mode() === 'RETIRADA') return true;
    const zone = this.selectedZone();
    return zone != null && this.subtotal() >= zone.minimumOrderValue;
  }

  constructor() {
    this.loadAddresses();
    this.loadZones();
    effect(() => {
      const addr = this.addresses().find((a) => a.id === this.selectedAddressId());
      untracked(() => this.updateDistance(addr));
    });
  }

  private async updateDistance(addr: CustomerAddressResponse | undefined): Promise<void> {
    this.distance.set(null);
    if (!addr) return;
    const where = await geocode(addr);
    if (!where || this.selectedAddressId() !== addr.id) return;
    const full = `${addr.street}, ${addr.number}, ${addr.neighborhood}, ${addr.city} - ${addr.state}, ${addr.zipCode}`;
    this.distance.set({
      text: formatDistance(distanceKm(RESTAURANT, where)),
      route: directionsLink(full, RESTAURANT.fallbackAddress),
    });
  }

  private async loadZones(): Promise<void> {
    try {
      const units = await this.unitsService.list();
      const unitId = units[0]?.id ?? 1;
      const zones = await firstValueFrom(
        this.api.get<DeliveryZoneResponse[]>(`/delivery-zones?unitId=${unitId}`),
      );
      this.zones.set(zones.filter((z) => z.active));
    } catch {
      this.zones.set([]);
    }
  }

  /** Zona do endereco selecionado (undefined = bairro sem entrega). */
  selectedZone(): DeliveryZoneResponse | undefined {
    const addr = this.addresses().find((a) => a.id === this.selectedAddressId());
    return addr ? this.zones().find((z) => z.neighborhood === addr.neighborhood) : undefined;
  }

  private async loadAddresses(): Promise<void> {
    try {
      const me = await firstValueFrom(this.api.get<CustomerResponse>('/customers/me'));
      this.customerId.set(me.id);
      this.addresses.set(me.addresses);
      // Sem endereco (ex.: se cadastrou fora da area de entrega): comeca em retirada no balcao.
      if (me.addresses.length === 0) {
        this.showAddressForm.set(true);
        this.mode.set('RETIRADA');
      }
      const def = me.addresses.find((a) => a.isDefault) ?? me.addresses[0];
      if (def) this.selectedAddressId.set(def.id);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erro ao carregar seus endereços'));
    }
  }

  async saveAddress(): Promise<void> {
    const id = this.customerId();
    const a = this.addr;
    if (id == null) return;
    if (!a.street || !a.number || !a.neighborhood || !a.city || !a.state || !a.zipCode) {
      this.error.set('Preencha CEP, rua, número e bairro');
      return;
    }
    const cepError = this.cep.validate(a);
    if (cepError) {
      this.error.set(cepError);
      return;
    }
    this.savingAddress.set(true);
    this.error.set(null);
    try {
      const created = await firstValueFrom(
        this.api.post<CustomerAddressResponse>(`/customers/${id}/addresses`, {
          ...a,
          label: a.label || 'Casa',
          isDefault: this.addresses().length === 0,
        }),
      );
      this.addresses.update((list) => [...list, created]);
      this.selectedAddressId.set(created.id);
      this.showAddressForm.set(false);
      this.addr = { label: '', street: '', number: '', complement: '', neighborhood: '', city: 'São Paulo', state: 'SP', zipCode: '' };
      this.cep.reset();
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erro ao salvar o endereço'));
    } finally {
      this.savingAddress.set(false);
    }
  }

  async onCep(input: HTMLInputElement): Promise<void> {
    const lookup = this.cep.onInput(input.value, this.addr, this.zones());
    input.value = this.addr.zipCode; // mascara aplicada na hora, mesmo se a pessoa digitar letra
    await lookup;
  }

  selectAddress(id: number): void {
    this.selectedAddressId.set(id);
  }

  changeQty(productId: number, delta: number): void {
    this.cart.changeQty(productId, delta);
  }

  remove(productId: number): void {
    this.cart.remove(productId);
  }

  setNotes(productId: number, notes: string): void {
    this.cart.setNotes(productId, notes);
  }

  private paymentSummary(): string {
    const label = this.paymentMethods.find((m) => m.value === this.paymentMethod)?.label ?? this.paymentMethod;
    const change = this.paymentMethod === 'DINHEIRO' && this.changeFor ? ` - troco para R$ ${this.changeFor}` : '';
    return `Pagamento (simulado): ${label}${change}`;
  }

  private orderKey = '';
  private orderKeyFor = '';

  /**
   * Mesma chave enquanto o pedido for o mesmo: se o envio repetir (clique duplo, rede
   * lenta, tentar de novo depois de erro), o servidor devolve o pedido ja criado.
   * Mudou o carrinho, o endereco ou a forma de receber, e outro pedido: chave nova.
   */
  private idempotencyKey(body: unknown): string {
    const signature = JSON.stringify(body);
    if (signature !== this.orderKeyFor) {
      this.orderKeyFor = signature;
      this.orderKey = crypto.randomUUID();
    }
    return this.orderKey;
  }

  async placeOrder(): Promise<void> {
    if (!this.canPlace()) return;
    const pickup = this.mode() === 'RETIRADA';
    this.placing.set(true);
    this.error.set(null);
    try {
      const units = await this.unitsService.list();
      const unitId = units[0]?.id ?? 1;
      const body = {
        unitId,
        channel: pickup ? 'RETIRADA' : 'DELIVERY',
        customerAddressId: pickup ? null : this.selectedAddressId(),
        notes: [this.orderNotes.trim(), this.paymentSummary()].filter(Boolean).join(' | '),
        items: this.lines().map((l) => ({ productId: l.productId, quantity: l.quantity, notes: l.notes || null })),
        paymentMethod: this.paymentMethod,
      };
      const order = await firstValueFrom(
        this.api.post<OrderResponse>('/orders', body, { 'Idempotency-Key': this.idempotencyKey(body) }),
      );
      this.cart.clear();
      this.router.navigate(['/app/pedidos', order.id]);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erro ao finalizar o pedido'));
    } finally {
      this.placing.set(false);
    }
  }
}
