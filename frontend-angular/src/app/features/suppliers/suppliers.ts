import { Component, inject, signal, effect, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { currency } from '../../core/format';

interface Supplier { id: number; name: string; phone: string | null; document: string; }
interface SupplierProduct { id: number; inventoryItemName: string; price: number; unitOfMeasure: string; }
interface StockItem { id: number; name: string; unitOfMeasure: string; }
interface Quote { supplierId: number; supplierName: string; price: number; unitOfMeasure: string; }
interface Purchase {
  id: number;
  supplierName: string;
  status: string;
  total: number;
  items: { inventoryItemName: string; quantity: number; unitPrice: number }[];
}
interface Line { inventoryItemId: number | null; quantity: number; unitPrice: number; }

const STATUS: Record<string, { label: string; cls: string }> = {
  RASCUNHO: { label: 'Rascunho', cls: 'badge-gray' },
  ENVIADO: { label: 'Enviado', cls: 'badge-blue' },
  RECEBIDO: { label: 'Recebido', cls: 'badge-green' },
  CANCELADO: { label: 'Cancelado', cls: 'badge-red' },
};

@Component({
  selector: 'app-suppliers',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="mb-6">
        <h1 class="page-title">Fornecedores e compras</h1>
        <p class="page-sub">Cadastre fornecedores, monte o catálogo de preços, compare cotações e controle os pedidos de compra.</p>
      </div>

      @if (error()) {
        <div class="alert-error mb-4"><span>{{ error() }}</span><button (click)="error.set(null)" class="text-red-400">×</button></div>
      }

      <!-- Fornecedores -->
      <section class="glass rounded-2xl p-5 mb-6">
        <h2 class="section-title">Novo fornecedor</h2>
        <form (ngSubmit)="createSupplier()" class="grid grid-cols-1 md:grid-cols-[1fr_12rem_14rem_auto] gap-3 items-end">
          <div>
            <label class="field-label" for="s-name">Nome</label>
            <input id="s-name" class="field" name="sname" [(ngModel)]="sName" placeholder="Razão social ou nome fantasia" required />
          </div>
          <div>
            <label class="field-label" for="s-phone">Telefone</label>
            <input id="s-phone" class="field" name="sphone" [(ngModel)]="sPhone" placeholder="(11) 90000-0000" />
          </div>
          <div>
            <label class="field-label" for="s-doc">CNPJ</label>
            <input id="s-doc" class="field" name="sdoc" [(ngModel)]="sDoc" placeholder="Somente números" required />
          </div>
          <button type="submit" class="btn-brand rounded-xl px-4 py-2 text-sm">Criar</button>
        </form>
      </section>

      <section class="glass rounded-2xl overflow-hidden mb-6">
        <div class="px-5 pt-5"><h2 class="section-title">Fornecedores</h2></div>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>Nome</th><th>CNPJ</th><th>Telefone</th><th class="num">Catálogo</th></tr></thead>
            <tbody>
              @for (s of suppliers(); track s.id) {
                <tr>
                  <td>{{ s.name }}</td>
                  <td>{{ s.document }}</td>
                  <td>{{ s.phone || '—' }}</td>
                  <td class="num">
                    <button class="btn-ghost" (click)="openCatalog(s)">
                      {{ selected()?.id === s.id ? 'Aberto' : 'Abrir catálogo' }}
                    </button>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="empty">Nenhum fornecedor cadastrado.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      @if (selected(); as sel) {
        <section class="glass rounded-2xl p-5 mb-6">
          <div class="flex items-center justify-between mb-3">
            <h2 class="section-title !mb-0">Catálogo de {{ sel.name }}</h2>
            <button class="btn-ghost" (click)="selected.set(null)">Fechar</button>
          </div>
          <div class="table-wrap mb-4">
            <table class="data-table">
              <thead><tr><th>Ingrediente</th><th class="num">Preço</th><th>Unidade</th></tr></thead>
              <tbody>
                @for (p of catalog(); track p.id) {
                  <tr><td>{{ p.inventoryItemName }}</td><td class="num">{{ currency(p.price) }}</td><td>{{ p.unitOfMeasure }}</td></tr>
                } @empty {
                  <tr><td colspan="3" class="empty">Este fornecedor ainda não tem itens no catálogo.</td></tr>
                }
              </tbody>
            </table>
          </div>
          <form (ngSubmit)="addCatalogItem()" class="grid grid-cols-1 md:grid-cols-[1fr_10rem_10rem_auto] gap-3 items-end">
            <div>
              <label class="field-label" for="c-item">Ingrediente</label>
              <select id="c-item" class="field" name="citem" [(ngModel)]="cItem" required>
                <option [ngValue]="null">Escolha…</option>
                @for (i of stock(); track i.id) { <option [ngValue]="i.id">{{ i.name }}</option> }
              </select>
            </div>
            <div>
              <label class="field-label" for="c-price">Preço (R$)</label>
              <input id="c-price" class="field" name="cprice" type="number" step="any" min="0" [(ngModel)]="cPrice" required />
            </div>
            <div>
              <label class="field-label" for="c-unit">Unidade</label>
              <input id="c-unit" class="field" name="cunit" [(ngModel)]="cUnit" placeholder="kg, un, l…" required />
            </div>
            <button type="submit" class="btn-ghost">Adicionar / atualizar</button>
          </form>
        </section>
      }

      <!-- Cotação -->
      <section class="glass rounded-2xl p-5 mb-6">
        <h2 class="section-title">Cotação comparativa</h2>
        <label class="field-label" for="q-item">Ingrediente</label>
        <select id="q-item" class="field md:max-w-md mb-4" [(ngModel)]="quoteItem" (ngModelChange)="loadQuote()">
          <option [ngValue]="null">Escolha um ingrediente…</option>
          @for (i of stock(); track i.id) { <option [ngValue]="i.id">{{ i.name }}</option> }
        </select>
        @if (quoteItem != null) {
          <div class="table-wrap">
            <table class="data-table">
              <thead><tr><th>Fornecedor</th><th class="num">Preço</th><th>Unidade</th><th></th></tr></thead>
              <tbody>
                @for (q of quotes(); track q.supplierId; let first = $first) {
                  <tr>
                    <td>{{ q.supplierName }}</td>
                    <td class="num">{{ currency(q.price) }}</td>
                    <td>{{ q.unitOfMeasure }}</td>
                    <td>@if (first) { <span class="badge badge-green">Melhor preço</span> }</td>
                  </tr>
                } @empty {
                  <tr><td colspan="4" class="empty">Nenhum fornecedor cotou este ingrediente.</td></tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <!-- Novo pedido de compra -->
      <section class="glass rounded-2xl p-5 mb-6">
        <h2 class="section-title">Novo pedido de compra</h2>
        <label class="field-label" for="p-sup">Fornecedor</label>
        <select id="p-sup" class="field md:max-w-md mb-4" [(ngModel)]="pSupplier">
          <option [ngValue]="null">Escolha um fornecedor…</option>
          @for (s of suppliers(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
        </select>

        <div class="space-y-2">
          @for (l of lines; track l; let i = $index) {
            <div class="grid grid-cols-1 md:grid-cols-[1fr_8rem_9rem_auto] gap-2 items-center">
              <select class="field" [(ngModel)]="l.inventoryItemId" [name]="'li' + i">
                <option [ngValue]="null">Ingrediente…</option>
                @for (s of stock(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
              </select>
              <input class="field" type="number" step="any" min="0" [(ngModel)]="l.quantity" [name]="'lq' + i" placeholder="Qtd" />
              <input class="field" type="number" step="any" min="0" [(ngModel)]="l.unitPrice" [name]="'lp' + i" placeholder="Preço unit." />
              <button type="button" class="btn-ghost btn-danger" title="Remover" (click)="removeLine(i)">×</button>
            </div>
          } @empty {
            <p class="text-sm text-neutral-500">Nenhum item. Adicione os ingredientes que serão comprados.</p>
          }
        </div>

        <div class="flex items-center justify-between mt-4">
          <button type="button" class="btn-ghost" (click)="addLine()">+ Item</button>
          <div class="flex items-center gap-4">
            <span class="text-sm text-neutral-400">Total: <strong class="text-neutral-100">{{ currency(draftTotal()) }}</strong></span>
            <button type="button" class="btn-brand rounded-xl px-4 py-2 text-sm" [disabled]="!canCreatePurchase()" (click)="createPurchase()">
              Criar pedido (rascunho)
            </button>
          </div>
        </div>
      </section>

      <!-- Pedidos -->
      <section class="glass rounded-2xl overflow-hidden">
        <div class="px-5 pt-5"><h2 class="section-title">Pedidos de compra</h2></div>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>#</th><th>Fornecedor</th><th>Itens</th><th class="num">Total</th><th>Status</th><th class="num">Ações</th></tr></thead>
            <tbody>
              @for (p of purchases(); track p.id) {
                <tr>
                  <td>{{ p.id }}</td>
                  <td>{{ p.supplierName }}</td>
                  <td class="text-xs text-neutral-400">
                    @for (it of p.items; track $index) { <div>{{ it.inventoryItemName }} × {{ it.quantity }} · {{ currency(it.unitPrice) }}</div> }
                  </td>
                  <td class="num">{{ currency(p.total) }}</td>
                  <td><span class="badge" [class]="statusOf(p.status).cls">{{ statusOf(p.status).label }}</span></td>
                  <td class="num">
                    <div class="inline-flex gap-2">
                      @if (p.status === 'RASCUNHO') { <button class="btn-ghost" (click)="act(p, 'send')">Enviar</button> }
                      @if (p.status === 'ENVIADO') { <button class="btn-ghost" (click)="act(p, 'receive')">Receber</button> }
                      @if (p.status === 'RASCUNHO' || p.status === 'ENVIADO') {
                        <button class="btn-ghost btn-danger" (click)="act(p, 'cancel')">Cancelar</button>
                      }
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="empty">Nenhum pedido de compra.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
})
export class Suppliers {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly currency = currency;
  suppliers = signal<Supplier[]>([]);
  stock = signal<StockItem[]>([]);
  purchases = signal<Purchase[]>([]);
  catalog = signal<SupplierProduct[]>([]);
  quotes = signal<Quote[]>([]);
  selected = signal<Supplier | null>(null);
  error = signal<string | null>(null);

  sName = '';
  sPhone = '';
  sDoc = '';
  cItem: number | null = null;
  cPrice: number | null = null;
  cUnit = '';
  quoteItem: number | null = null;
  pSupplier: number | null = null;
  lines: Line[] = [];

  /** As linhas do rascunho mudam por mutação (ngModel), então o total é calculado a cada ciclo. */
  draftTotal(): number {
    return this.lines.reduce((sum, l) => sum + (Number(l.quantity) || 0) * (Number(l.unitPrice) || 0), 0);
  }

  constructor() {
    effect(() => {
      const u = this.auth.unitId();
      if (u == null) return;
      untracked(() => {
        this.loadSuppliers();
        this.loadPurchases();
        this.api.get<StockItem[]>(`/inventory/items?unitId=${u}`).subscribe({
          next: (s) => this.stock.set(s),
          error: (e) => this.fail(e, 'Erro ao carregar estoque'),
        });
      });
    });
  }

  private fail(e: unknown, fallback: string): void {
    this.error.set(apiErrorMessage(e, fallback));
  }

  statusOf(status: string): { label: string; cls: string } {
    return STATUS[status] ?? { label: status, cls: 'badge-gray' };
  }

  loadSuppliers(): void {
    this.api.get<Supplier[]>(`/suppliers?unitId=${this.auth.unitId()}`).subscribe({
      next: (s) => this.suppliers.set(s),
      error: (e) => this.fail(e, 'Erro ao carregar fornecedores'),
    });
  }

  loadPurchases(): void {
    this.api.get<Purchase[]>(`/purchases?unitId=${this.auth.unitId()}`).subscribe({
      next: (p) => this.purchases.set(p),
      error: (e) => this.fail(e, 'Erro ao carregar compras'),
    });
  }

  createSupplier(): void {
    this.error.set(null);
    const body = { unitId: this.auth.unitId(), name: this.sName, phone: this.sPhone, document: this.sDoc };
    this.api.post('/suppliers', body).subscribe({
      next: () => {
        this.sName = '';
        this.sPhone = '';
        this.sDoc = '';
        this.loadSuppliers();
      },
      error: (e) => this.fail(e, 'Erro ao criar fornecedor'),
    });
  }

  openCatalog(s: Supplier): void {
    this.selected.set(s);
    this.loadCatalog();
  }

  private loadCatalog(): void {
    const s = this.selected();
    if (!s) return;
    this.api.get<SupplierProduct[]>(`/suppliers/${s.id}/products`).subscribe({
      next: (c) => this.catalog.set(c),
      error: (e) => this.fail(e, 'Erro ao carregar catálogo'),
    });
  }

  addCatalogItem(): void {
    const s = this.selected();
    if (!s) return;
    this.error.set(null);
    const body = { inventoryItemId: this.cItem, price: this.cPrice, unitOfMeasure: this.cUnit };
    this.api.post(`/suppliers/${s.id}/products`, body).subscribe({
      next: () => {
        this.cPrice = null;
        this.cUnit = '';
        this.loadCatalog();
      },
      error: (e) => this.fail(e, 'Erro ao salvar item do catálogo'),
    });
  }

  loadQuote(): void {
    if (this.quoteItem == null) {
      this.quotes.set([]);
      return;
    }
    this.api.get<Quote[]>(`/suppliers/quote?inventoryItemId=${this.quoteItem}`).subscribe({
      next: (q) => this.quotes.set(q),
      error: (e) => this.fail(e, 'Erro ao buscar cotação'),
    });
  }

  addLine(): void {
    this.lines.push({ inventoryItemId: null, quantity: 1, unitPrice: 0 });
  }

  removeLine(i: number): void {
    this.lines.splice(i, 1);
  }

  canCreatePurchase(): boolean {
    return this.pSupplier != null && this.lines.length > 0 && this.lines.every((l) => l.inventoryItemId != null && l.quantity > 0);
  }

  createPurchase(): void {
    this.error.set(null);
    const body = { unitId: this.auth.unitId(), supplierId: this.pSupplier, items: this.lines };
    this.api.post('/purchases', body).subscribe({
      next: () => {
        this.lines = [];
        this.loadPurchases();
      },
      error: (e) => this.fail(e, 'Erro ao criar pedido de compra'),
    });
  }

  act(p: Purchase, action: 'send' | 'receive' | 'cancel'): void {
    this.error.set(null);
    const req =
      action === 'receive' ? this.api.post(`/purchases/${p.id}/receive`) : this.api.patch(`/purchases/${p.id}/${action}`);
    req.subscribe({
      next: () => this.loadPurchases(),
      error: (e) => this.fail(e, 'Erro ao atualizar pedido de compra'),
    });
  }
}
