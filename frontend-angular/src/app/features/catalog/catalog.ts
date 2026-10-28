import { Component, inject, signal, effect, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { CategoryResponse, KitchenSectorResponse, ProductResponse } from '../../core/models';
import { currency } from '../../core/format';

interface ProductForm {
  id: number | null;
  categoryId: number | null;
  kitchenSectorId: number | null;
  name: string;
  description: string;
  preparationMethod: string;
  basePrice: string;
  imageUrl: string;
  prepTimeMinutes: string;
  available: boolean;
}

@Component({
  selector: 'app-catalog',
  imports: [FormsModule],
  templateUrl: './catalog.html',
})
export class Catalog {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly currency = currency;
  readonly unitId = this.auth.unitId;

  categories = signal<CategoryResponse[]>([]);
  products = signal<ProductResponse[]>([]);
  sectors = signal<KitchenSectorResponse[]>([]);
  selectedCategoryId = signal<number | null>(null);
  loadingProducts = signal(false);
  error = signal<string | null>(null);

  newCategoryName = '';
  editingCategoryId = signal<number | null>(null);
  editingCategoryName = '';
  productForm = signal<ProductForm | null>(null);
  savingProduct = signal(false);

  constructor() {
    // Aguarda o unitId ficar disponivel (o layout carrega as unidades async)
    // antes de buscar categorias/produtos. Equivalente ao "enabled: unitId != null"
    // que o React Query usava.
    effect(() => {
      const u = this.unitId();
      if (u == null) return;
      untracked(() => {
        this.loadCategories();
        this.loadProducts();
        this.loadSectors();
      });
    });
  }

  private loadCategories(): void {
    this.api.get<CategoryResponse[]>(`/categories?unitId=${this.unitId()}`).subscribe({
      next: (c) => this.categories.set(c),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar categorias')),
    });
  }

  private loadSectors(): void {
    this.api.get<KitchenSectorResponse[]>('/kitchen-sectors').subscribe({
      next: (all) => this.sectors.set(all.filter((s) => s.unitId === this.unitId())),
      error: () => this.sectors.set([]),
    });
  }

  loadProducts(): void {
    const cat = this.selectedCategoryId();
    this.loadingProducts.set(true);
    const path = `/products?unitId=${this.unitId()}${cat ? `&categoryId=${cat}` : ''}`;
    this.api.get<ProductResponse[]>(path).subscribe({
      next: (p) => {
        this.products.set(p);
        this.loadingProducts.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'Erro ao carregar produtos'));
        this.loadingProducts.set(false);
      },
    });
  }

  selectCategory(id: number | null): void {
    this.selectedCategoryId.set(id);
    this.loadProducts();
  }

  createCategory(): void {
    const name = this.newCategoryName.trim();
    if (!name) return;
    this.api.post<CategoryResponse>('/categories', { unitId: this.unitId(), name }).subscribe({
      next: () => {
        this.newCategoryName = '';
        this.loadCategories();
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao criar categoria')),
    });
  }

  startEditCategory(cat: CategoryResponse): void {
    this.editingCategoryId.set(cat.id);
    this.editingCategoryName = cat.name;
  }

  saveCategory(cat: CategoryResponse): void {
    const name = this.editingCategoryName.trim();
    if (!name) return;
    this.api.put<CategoryResponse>(`/categories/${cat.id}`, { unitId: this.unitId(), name, displayOrder: cat.displayOrder }).subscribe({
      next: () => {
        this.editingCategoryId.set(null);
        this.loadCategories();
        this.loadProducts();
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao renomear categoria')),
    });
  }

  deleteCategory(cat: CategoryResponse): void {
    if (!confirm(`Excluir a categoria "${cat.name}"?`)) return;
    this.api.delete<void>(`/categories/${cat.id}`).subscribe({
      next: () => {
        if (this.selectedCategoryId() === cat.id) this.selectedCategoryId.set(null);
        this.loadCategories();
        this.loadProducts();
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao excluir categoria')),
    });
  }

  openNewProduct(): void {
    this.productForm.set({
      id: null,
      categoryId: this.selectedCategoryId() ?? this.categories()[0]?.id ?? null,
      kitchenSectorId: this.sectors()[0]?.id ?? null,
      name: '',
      description: '',
      preparationMethod: '',
      basePrice: '',
      imageUrl: '',
      prepTimeMinutes: '',
      available: false,
    });
  }

  openEditProduct(p: ProductResponse): void {
    this.productForm.set({
      id: p.id,
      categoryId: p.categoryId,
      kitchenSectorId: p.kitchenSectorId,
      name: p.name,
      description: p.description ?? '',
      preparationMethod: p.preparationMethod ?? '',
      basePrice: String(p.basePrice),
      imageUrl: p.imageUrl ?? '',
      prepTimeMinutes: p.prepTimeMinutes != null ? String(p.prepTimeMinutes) : '',
      available: p.available,
    });
  }

  closeForm(): void {
    this.productForm.set(null);
  }

  saveProduct(): void {
    const form = this.productForm();
    if (!form) return;
    const payload = {
      unitId: this.unitId(),
      categoryId: form.categoryId,
      kitchenSectorId: form.kitchenSectorId,
      name: form.name,
      description: form.description || null,
      preparationMethod: form.preparationMethod.trim() || null,
      basePrice: Number(form.basePrice),
      imageUrl: form.imageUrl.trim() || null,
      prepTimeMinutes: form.prepTimeMinutes ? Number(form.prepTimeMinutes) : null,
      available: form.available,
    };
    this.savingProduct.set(true);
    const req = form.id
      ? this.api.put<ProductResponse>(`/products/${form.id}`, payload)
      : this.api.post<ProductResponse>('/products', payload);
    req.subscribe({
      next: () => {
        this.savingProduct.set(false);
        this.productForm.set(null);
        this.loadProducts();
      },
      error: (e) => {
        this.savingProduct.set(false);
        this.error.set(apiErrorMessage(e, 'Erro ao salvar produto'));
      },
    });
  }

  deleteProduct(p: ProductResponse): void {
    if (!confirm(`Excluir o produto "${p.name}"?`)) return;
    this.api.delete<void>(`/products/${p.id}`).subscribe({
      next: () => this.loadProducts(),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao excluir produto')),
    });
  }
}
