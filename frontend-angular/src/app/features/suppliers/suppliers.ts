import { Component, inject, signal, effect, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

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

@Component({
  selector: 'app-suppliers',
  host: { class: 'page-basic' },
  imports: [FormsModule],
  template: `
    <h1>Fornecedores e compras</h1>
    @if (error()) { <p>{{ error() }}</p> }

    <h2>Novo fornecedor</h2>
    <form (ngSubmit)="createSupplier()">
      <input name="sname" [(ngModel)]="sName" placeholder="Nome" required />
      <input name="sphone" [(ngModel)]="sPhone" placeholder="Telefone" />
      <input name="sdoc" [(ngModel)]="sDoc" placeholder="CNPJ" required />
      <button type="submit">Criar</button>
    </form>

    <h2>Fornecedores</h2>
    <table border="1">
      <tr><th>Nome</th><th>CNPJ</th><th>Telefone</th><th></th></tr>
      @for (s of suppliers(); track s.id) {
        <tr>
          <td>{{ s.name }}</td><td>{{ s.document }}</td><td>{{ s.phone }}</td>
          <td><button (click)="openCatalog(s)">Catálogo</button></td>
        </tr>
      }
    </table>

    @if (selected(); as sel) {
      <h2>Catálogo de {{ sel.name }}</h2>
      <table border="1">
        <tr><th>Ingrediente</th><th>Preço</th><th>Unidade</th></tr>
        @for (p of catalog(); track p.id) {
          <tr><td>{{ p.inventoryItemName }}</td><td>{{ p.price }}</td><td>{{ p.unitOfMeasure }}</td></tr>
        }
      </table>
      <form (ngSubmit)="addCatalogItem()">
        <select name="citem" [(ngModel)]="cItem">
          @for (i of stock(); track i.id) { <option [ngValue]="i.id">{{ i.name }}</option> }
        </select>
        <input name="cprice" type="number" step="any" [(ngModel)]="cPrice" placeholder="Preço" />
        <input name="cunit" [(ngModel)]="cUnit" placeholder="Unidade (kg, un...)" />
        <button type="submit">Adicionar/atualizar</button>
      </form>
    }

    <h2>Cotação comparativa</h2>
    <select [(ngModel)]="quoteItem" (ngModelChange)="loadQuote()">
      <option [ngValue]="null">-- ingrediente --</option>
      @for (i of stock(); track i.id) { <option [ngValue]="i.id">{{ i.name }}</option> }
    </select>
    <table border="1">
      <tr><th>Fornecedor</th><th>Preço</th><th>Unidade</th></tr>
      @for (q of quotes(); track q.supplierId) {
        <tr><td>{{ q.supplierName }}</td><td>{{ q.price }}</td><td>{{ q.unitOfMeasure }}</td></tr>
      }
    </table>

    <h2>Novo pedido de compra</h2>
    <select [(ngModel)]="pSupplier">
      <option [ngValue]="null">-- fornecedor --</option>
      @for (s of suppliers(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
    </select>
    @for (l of lines; track l; let i = $index) {
      <div>
        <select [(ngModel)]="l.inventoryItemId">
          @for (s of stock(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
        </select>
        <input type="number" step="any" [(ngModel)]="l.quantity" placeholder="Qtd" />
        <input type="number" step="any" [(ngModel)]="l.unitPrice" placeholder="Preço unit." />
        <button (click)="removeLine(i)">Remover</button>
      </div>
    }
    <button (click)="addLine()">+ Item</button>
    <button (click)="createPurchase()">Criar pedido (rascunho)</button>

    <h2>Pedidos de compra</h2>
    <table border="1">
      <tr><th>#</th><th>Fornecedor</th><th>Status</th><th>Total</th><th>Itens</th><th>Ações</th></tr>
      @for (p of purchases(); track p.id) {
        <tr>
          <td>{{ p.id }}</td><td>{{ p.supplierName }}</td><td>{{ p.status }}</td><td>{{ p.total }}</td>
          <td>
            @for (it of p.items; track $index) { <div>{{ it.inventoryItemName }} x{{ it.quantity }} @ {{ it.unitPrice }}</div> }
          </td>
          <td>
            @if (p.status === 'RASCUNHO') { <button (click)="act(p, 'send')">Enviar</button> }
            @if (p.status === 'ENVIADO') { <button (click)="act(p, 'receive')">Receber</button> }
            @if (p.status === 'RASCUNHO' || p.status === 'ENVIADO') { <button (click)="act(p, 'cancel')">Cancelar</button> }
          </td>
        </tr>
      }
    </table>
  `,
})
export class Suppliers {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

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
      next: () => this.loadCatalog(),
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
