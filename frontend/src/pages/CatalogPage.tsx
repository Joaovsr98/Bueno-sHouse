import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthContext';
import { api, ApiError } from '../lib/api';

type CategoryResponse = {
  id: number;
  unitId: number;
  name: string;
  displayOrder: number;
  active: boolean;
};

type ProductResponse = {
  id: number;
  unitId: number;
  categoryId: number;
  categoryName: string;
  name: string;
  description: string | null;
  basePrice: number;
  imageUrl: string | null;
  prepTimeMinutes: number | null;
  kitchenSectorId: number | null;
  available: boolean;
  featured: boolean;
};

function currency(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

export function CatalogPage() {
  const { unitId } = useAuth();
  const queryClient = useQueryClient();

  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [productForm, setProductForm] = useState<{
    id: number | null;
    name: string;
    description: string;
    basePrice: string;
  } | null>(null);

  const categoriesQuery = useQuery({
    queryKey: ['categories', unitId],
    queryFn: () => api.get<CategoryResponse[]>(`/categories?unitId=${unitId}`),
    enabled: unitId != null,
  });

  const productsQuery = useQuery({
    queryKey: ['products', unitId, selectedCategoryId],
    queryFn: () =>
      api.get<ProductResponse[]>(
        `/products?unitId=${unitId}${selectedCategoryId ? `&categoryId=${selectedCategoryId}` : ''}`,
      ),
    enabled: unitId != null,
  });

  const createCategory = useMutation({
    mutationFn: (name: string) => api.post<CategoryResponse>('/categories', { unitId, name }),
    onSuccess: () => {
      setNewCategoryName('');
      queryClient.invalidateQueries({ queryKey: ['categories', unitId] });
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao criar categoria'),
  });

  const deleteCategory = useMutation({
    mutationFn: (id: number) => api.delete<void>(`/categories/${id}`),
    onSuccess: () => {
      if (selectedCategoryId) setSelectedCategoryId(null);
      queryClient.invalidateQueries({ queryKey: ['categories', unitId] });
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao excluir categoria'),
  });

  const saveProduct = useMutation({
    mutationFn: (form: NonNullable<typeof productForm>) => {
      const payload = {
        unitId,
        categoryId: selectedCategoryId,
        name: form.name,
        description: form.description || null,
        basePrice: Number(form.basePrice),
      };
      return form.id
        ? api.put<ProductResponse>(`/products/${form.id}`, payload)
        : api.post<ProductResponse>('/products', payload);
    },
    onSuccess: () => {
      setProductForm(null);
      queryClient.invalidateQueries({ queryKey: ['products', unitId] });
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao salvar produto'),
  });

  const deleteProduct = useMutation({
    mutationFn: (id: number) => api.delete<void>(`/products/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['products', unitId] }),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao excluir produto'),
  });

  if (unitId == null) {
    return <div className="p-6 text-sm text-neutral-400">Carregando unidade...</div>;
  }

  return (
    <div className="flex h-screen">
      <div className="w-64 shrink-0 border-r border-neutral-800 flex flex-col">
        <div className="p-4 border-b border-neutral-800">
          <h2 className="text-sm font-semibold">Categorias</h2>
        </div>

        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          <button
            onClick={() => setSelectedCategoryId(null)}
            className={`w-full text-left rounded-lg px-3 py-2 text-sm ${
              selectedCategoryId === null
                ? 'bg-neutral-800 text-neutral-100'
                : 'text-neutral-400 hover:bg-neutral-900'
            }`}
          >
            Todas
          </button>

          {categoriesQuery.data?.map((cat) => (
            <div
              key={cat.id}
              className={`group flex items-center rounded-lg px-3 py-2 text-sm cursor-pointer ${
                selectedCategoryId === cat.id
                  ? 'bg-neutral-800 text-neutral-100'
                  : 'text-neutral-400 hover:bg-neutral-900'
              }`}
              onClick={() => setSelectedCategoryId(cat.id)}
            >
              <span className="flex-1 truncate">{cat.name}</span>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  if (confirm(`Excluir a categoria "${cat.name}"?`)) deleteCategory.mutate(cat.id);
                }}
                className="opacity-0 group-hover:opacity-100 text-neutral-500 hover:text-red-400 text-xs ml-2"
              >
                excluir
              </button>
            </div>
          ))}
        </div>

        <form
          className="p-3 border-t border-neutral-800 flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            if (newCategoryName.trim()) createCategory.mutate(newCategoryName.trim());
          }}
        >
          <input
            value={newCategoryName}
            onChange={(e) => setNewCategoryName(e.target.value)}
            placeholder="Nova categoria"
            className="flex-1 min-w-0 rounded-lg bg-neutral-900 border border-neutral-700 px-2 py-1.5 text-xs outline-none focus:border-neutral-500"
          />
          <button
            type="submit"
            disabled={createCategory.isPending}
            className="rounded-lg bg-neutral-100 text-neutral-900 px-3 text-xs font-medium disabled:opacity-50"
          >
            +
          </button>
        </form>
      </div>

      <div className="flex-1 min-w-0 overflow-y-auto">
        <div className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h1 className="text-lg font-semibold">Produtos</h1>
            <button
              onClick={() =>
                setProductForm({ id: null, name: '', description: '', basePrice: '' })
              }
              disabled={selectedCategoryId === null}
              title={selectedCategoryId === null ? 'Selecione uma categoria para adicionar' : ''}
              className="rounded-lg bg-neutral-100 text-neutral-900 px-3 py-1.5 text-sm font-medium disabled:opacity-40"
            >
              + Novo produto
            </button>
          </div>

          {error && (
            <div className="mb-4 rounded-lg bg-red-950 border border-red-800 p-3 text-sm text-red-300 flex justify-between">
              <span>{error}</span>
              <button onClick={() => setError(null)} className="text-red-400">
                ×
              </button>
            </div>
          )}

          {productsQuery.isLoading && (
            <p className="text-sm text-neutral-500">Carregando produtos...</p>
          )}

          {productsQuery.data?.length === 0 && (
            <p className="text-sm text-neutral-500">Nenhum produto encontrado.</p>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {productsQuery.data?.map((product) => (
              <div
                key={product.id}
                className="rounded-xl border border-neutral-800 bg-neutral-900 p-4 flex flex-col"
              >
                <div className="flex items-start justify-between gap-2">
                  <h3 className="text-sm font-medium">{product.name}</h3>
                  {!product.available && (
                    <span className="text-[10px] uppercase tracking-wide text-neutral-500 border border-neutral-700 rounded px-1.5 py-0.5 shrink-0">
                      Indisponível
                    </span>
                  )}
                </div>
                <p className="text-xs text-neutral-500 mt-1 mb-3 line-clamp-2 flex-1">
                  {product.description || 'Sem descrição'}
                </p>
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold">{currency(product.basePrice)}</span>
                  <div className="flex gap-2 text-xs">
                    <button
                      onClick={() =>
                        setProductForm({
                          id: product.id,
                          name: product.name,
                          description: product.description ?? '',
                          basePrice: String(product.basePrice),
                        })
                      }
                      className="text-neutral-400 hover:text-neutral-200"
                    >
                      editar
                    </button>
                    <button
                      onClick={() => {
                        if (confirm(`Excluir o produto "${product.name}"?`))
                          deleteProduct.mutate(product.id);
                      }}
                      className="text-neutral-500 hover:text-red-400"
                    >
                      excluir
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {productForm && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-6 z-10">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              saveProduct.mutate(productForm);
            }}
            className="w-full max-w-sm rounded-xl border border-neutral-800 bg-neutral-900 p-6 space-y-4"
          >
            <h2 className="text-sm font-semibold">
              {productForm.id ? 'Editar produto' : 'Novo produto'}
            </h2>

            <div className="space-y-1">
              <label className="text-xs text-neutral-400">Nome</label>
              <input
                required
                value={productForm.name}
                onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
                className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs text-neutral-400">Descrição</label>
              <textarea
                value={productForm.description}
                onChange={(e) => setProductForm({ ...productForm, description: e.target.value })}
                className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
                rows={2}
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs text-neutral-400">Preço base (R$)</label>
              <input
                required
                type="number"
                step="0.01"
                min="0"
                value={productForm.basePrice}
                onChange={(e) => setProductForm({ ...productForm, basePrice: e.target.value })}
                className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setProductForm(null)}
                className="text-sm text-neutral-400 px-3 py-1.5"
              >
                Cancelar
              </button>
              <button
                type="submit"
                disabled={saveProduct.isPending}
                className="rounded-lg bg-neutral-100 text-neutral-900 px-4 py-1.5 text-sm font-medium disabled:opacity-50"
              >
                Salvar
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
