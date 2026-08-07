import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { api, ApiError } from '../lib/api';

type CategoryResponse = { id: number; unitId: number; name: string; displayOrder: number; active: boolean };

type ProductResponse = {
  id: number;
  categoryId: number;
  name: string;
  basePrice: number;
  available: boolean;
};

type OrderItemResponse = {
  id: number;
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  notes: string | null;
  status: string;
};

type OrderResponse = {
  id: number;
  orderNumber: number;
  commandId: number | null;
  status: string;
  subtotal: number;
  total: number;
  notes: string | null;
  items: OrderItemResponse[];
};

type CartLine = {
  productId: number;
  productName: string;
  basePrice: number;
  quantity: number;
  notes: string;
};

const ORDER_STATUS_LABELS: Record<string, string> = {
  RECEBIDO: 'Recebido',
  ENVIADO_PARA_COZINHA: 'Enviado para cozinha',
  EM_PREPARO: 'Em preparo',
  PARCIALMENTE_PRONTO: 'Parcialmente pronto',
  PRONTO: 'Pronto',
  FINALIZADO: 'Finalizado',
  CANCELADO: 'Cancelado',
};

function currency(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

export function OrderPage() {
  const { serviceId, commandId } = useParams<{ serviceId: string; commandId: string }>();
  const { unitId } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [error, setError] = useState<string | null>(null);

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

  const ordersQuery = useQuery({
    queryKey: ['orders', unitId, commandId],
    queryFn: () => api.get<OrderResponse[]>(`/orders?unitId=${unitId}`),
    enabled: unitId != null,
  });

  const commandOrders = useMemo(
    () => (ordersQuery.data ?? []).filter((o) => String(o.commandId) === commandId),
    [ordersQuery.data, commandId],
  );

  const sendOrder = useMutation({
    mutationFn: () =>
      api.post<OrderResponse>('/orders', {
        unitId,
        channel: 'SALAO',
        commandId: Number(commandId),
        items: cart.map((line) => ({
          productId: line.productId,
          quantity: line.quantity,
          notes: line.notes || null,
        })),
      }),
    onSuccess: () => {
      setCart([]);
      queryClient.invalidateQueries({ queryKey: ['orders', unitId, commandId] });
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao enviar pedido'),
  });

  const sendToKitchen = useMutation({
    mutationFn: (orderId: number) => api.post<OrderResponse>(`/orders/${orderId}/send-to-kitchen`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['orders', unitId, commandId] }),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Erro ao enviar para a cozinha'),
  });

  function addToCart(product: ProductResponse) {
    setCart((prev) => {
      const existing = prev.find((l) => l.productId === product.id);
      if (existing) {
        return prev.map((l) =>
          l.productId === product.id ? { ...l, quantity: l.quantity + 1 } : l,
        );
      }
      return [...prev, { productId: product.id, productName: product.name, basePrice: product.basePrice, quantity: 1, notes: '' }];
    });
  }

  function updateCartLine(productId: number, patch: Partial<CartLine>) {
    setCart((prev) => prev.map((l) => (l.productId === productId ? { ...l, ...patch } : l)));
  }

  function removeCartLine(productId: number) {
    setCart((prev) => prev.filter((l) => l.productId !== productId));
  }

  const cartTotal = cart.reduce((sum, l) => sum + l.basePrice * l.quantity, 0);

  if (unitId == null) {
    return <div className="p-6 text-sm text-neutral-400">Carregando unidade...</div>;
  }

  return (
    <div className="flex h-screen">
      <div className="w-56 shrink-0 border-r border-neutral-800 flex flex-col">
        <div className="p-4 border-b border-neutral-800">
          <button
            onClick={() => navigate(`/atendimento/${serviceId}`)}
            className="text-xs text-neutral-500 hover:text-neutral-300"
          >
            ← Voltar
          </button>
          <h2 className="text-sm font-semibold mt-2">Categorias</h2>
        </div>
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          <button
            onClick={() => setSelectedCategoryId(null)}
            className={`w-full text-left rounded-lg px-3 py-2 text-sm ${
              selectedCategoryId === null ? 'bg-neutral-800 text-neutral-100' : 'text-neutral-400 hover:bg-neutral-900'
            }`}
          >
            Todas
          </button>
          {categoriesQuery.data?.map((cat) => (
            <button
              key={cat.id}
              onClick={() => setSelectedCategoryId(cat.id)}
              className={`w-full text-left rounded-lg px-3 py-2 text-sm ${
                selectedCategoryId === cat.id ? 'bg-neutral-800 text-neutral-100' : 'text-neutral-400 hover:bg-neutral-900'
              }`}
            >
              {cat.name}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 min-w-0 overflow-y-auto">
        <div className="p-6">
          <h1 className="text-lg font-semibold mb-4">Cardápio</h1>

          {error && (
            <div className="mb-4 rounded-lg bg-red-950 border border-red-800 p-3 text-sm text-red-300 flex justify-between">
              <span>{error}</span>
              <button onClick={() => setError(null)} className="text-red-400">×</button>
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
            {productsQuery.data?.map((product) => (
              <button
                key={product.id}
                disabled={!product.available}
                onClick={() => addToCart(product)}
                className="text-left rounded-xl border border-neutral-800 bg-neutral-900 p-4 hover:border-neutral-600 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <p className="text-sm font-medium">{product.name}</p>
                <p className="text-sm text-neutral-400 mt-1">{currency(product.basePrice)}</p>
              </button>
            ))}
          </div>

          <h2 className="text-sm font-semibold mb-3">Pedidos desta comanda</h2>
          {commandOrders.length === 0 && (
            <p className="text-sm text-neutral-500 mb-4">Nenhum pedido enviado ainda.</p>
          )}
          <div className="space-y-2">
            {commandOrders.map((order) => (
              <div key={order.id} className="rounded-xl border border-neutral-800 bg-neutral-900 p-4">
                <div className="flex items-center justify-between mb-2">
                  <p className="text-sm font-medium">Pedido #{order.orderNumber}</p>
                  <div className="flex items-center gap-2">
                    <span className="text-xs uppercase tracking-wide text-neutral-400">
                      {ORDER_STATUS_LABELS[order.status] ?? order.status}
                    </span>
                    {order.status === 'RECEBIDO' && (
                      <button
                        onClick={() => sendToKitchen.mutate(order.id)}
                        className="text-xs rounded-lg bg-neutral-100 text-neutral-900 px-2 py-1 font-medium"
                      >
                        Enviar p/ cozinha
                      </button>
                    )}
                  </div>
                </div>
                <ul className="text-xs text-neutral-400 space-y-0.5">
                  {order.items.map((item) => (
                    <li key={item.id}>
                      {item.quantity}x {item.productName} — {currency(item.subtotal)}
                    </li>
                  ))}
                </ul>
                <p className="text-sm font-semibold mt-2 mb-3">{currency(order.total)}</p>

                {!['CANCELADO', 'FINALIZADO'].includes(order.status) && (
                  <PaymentSection orderId={order.id} unitId={unitId} onError={setError} />
                )}
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="w-80 shrink-0 border-l border-neutral-800 flex flex-col">
        <div className="p-4 border-b border-neutral-800">
          <h2 className="text-sm font-semibold">Novo pedido</h2>
        </div>

        <div className="flex-1 overflow-y-auto p-3 space-y-3">
          {cart.length === 0 && (
            <p className="text-sm text-neutral-500">Clique num item do cardápio para adicionar.</p>
          )}
          {cart.map((line) => (
            <div key={line.productId} className="rounded-lg border border-neutral-800 p-3">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium">{line.productName}</p>
                <button
                  onClick={() => removeCartLine(line.productId)}
                  className="text-xs text-neutral-500 hover:text-red-400"
                >
                  remover
                </button>
              </div>
              <div className="flex items-center gap-2 mt-2">
                <button
                  onClick={() => updateCartLine(line.productId, { quantity: Math.max(1, line.quantity - 1) })}
                  className="w-6 h-6 rounded bg-neutral-800 text-sm"
                >
                  −
                </button>
                <span className="text-sm w-6 text-center">{line.quantity}</span>
                <button
                  onClick={() => updateCartLine(line.productId, { quantity: line.quantity + 1 })}
                  className="w-6 h-6 rounded bg-neutral-800 text-sm"
                >
                  +
                </button>
                <span className="text-xs text-neutral-500 ml-auto">
                  {currency(line.basePrice * line.quantity)}
                </span>
              </div>
              <input
                value={line.notes}
                onChange={(e) => updateCartLine(line.productId, { notes: e.target.value })}
                placeholder="Observações"
                className="w-full mt-2 rounded-lg bg-neutral-800 border border-neutral-700 px-2 py-1 text-xs outline-none focus:border-neutral-500"
              />
            </div>
          ))}
        </div>

        <div className="p-4 border-t border-neutral-800 space-y-3">
          <div className="flex justify-between text-sm">
            <span className="text-neutral-400">Total</span>
            <span className="font-semibold">{currency(cartTotal)}</span>
          </div>
          <button
            onClick={() => sendOrder.mutate()}
            disabled={cart.length === 0 || sendOrder.isPending}
            className="w-full rounded-lg bg-neutral-100 text-neutral-900 py-2 text-sm font-medium disabled:opacity-40"
          >
            {sendOrder.isPending ? 'Enviando...' : 'Enviar pedido'}
          </button>
        </div>
      </div>
    </div>
  );
}

type OrderBalanceResponse = {
  orderId: number;
  total: number;
  totalPaid: number;
  remaining: number;
  fullyPaid: boolean;
};

type CashRegisterResponse = { id: number; open: boolean };

const PAYMENT_METHODS = [
  { value: 'DINHEIRO', label: 'Dinheiro' },
  { value: 'CARTAO_CREDITO', label: 'Cartão de crédito' },
  { value: 'CARTAO_DEBITO', label: 'Cartão de débito' },
  { value: 'PIX', label: 'Pix' },
];

function PaymentSection({
  orderId,
  unitId,
  onError,
}: {
  orderId: number;
  unitId: number;
  onError: (msg: string) => void;
}) {
  const queryClient = useQueryClient();
  const [method, setMethod] = useState('PIX');
  const [amount, setAmount] = useState('');

  const balanceQuery = useQuery({
    queryKey: ['order-balance', orderId],
    queryFn: () => api.get<OrderBalanceResponse>(`/payments/balance?orderId=${orderId}`),
  });

  const cashRegisterQuery = useQuery({
    queryKey: ['cash-register-current', unitId],
    queryFn: async () => {
      try {
        return await api.get<CashRegisterResponse>(`/cash-registers/current?unitId=${unitId}`);
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) return null;
        throw err;
      }
    },
    enabled: method === 'DINHEIRO',
  });

  const registerPayment = useMutation({
    mutationFn: () => {
      if (method === 'DINHEIRO' && !cashRegisterQuery.data) {
        throw new ApiError(0, 'Nenhum caixa aberto — abra o caixa antes de receber em dinheiro.');
      }
      return api.post('/payments', {
        orderId,
        method,
        amount: Number(amount),
        cashRegisterId: method === 'DINHEIRO' ? cashRegisterQuery.data?.id : null,
      });
    },
    onSuccess: () => {
      setAmount('');
      queryClient.invalidateQueries({ queryKey: ['order-balance', orderId] });
    },
    onError: (err) => onError(err instanceof ApiError ? err.message : 'Erro ao registrar pagamento'),
  });

  const balance = balanceQuery.data;

  if (!balance) return null;

  if (balance.fullyPaid) {
    return <p className="text-xs text-green-400">Pago integralmente.</p>;
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (amount) registerPayment.mutate();
      }}
      className="flex flex-wrap gap-2 items-end border-t border-neutral-800 pt-3"
    >
      <span className="text-xs text-neutral-400 w-full">
        Saldo devedor: <span className="text-neutral-200 font-medium">{currency(balance.remaining)}</span>
      </span>
      <select
        value={method}
        onChange={(e) => setMethod(e.target.value)}
        className="rounded-lg bg-neutral-800 border border-neutral-700 px-2 py-1.5 text-xs"
      >
        {PAYMENT_METHODS.map((m) => (
          <option key={m.value} value={m.value}>
            {m.label}
          </option>
        ))}
      </select>
      <input
        required
        type="number"
        step="0.01"
        min="0.01"
        max={balance.remaining}
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        placeholder="Valor"
        className="w-24 rounded-lg bg-neutral-800 border border-neutral-700 px-2 py-1.5 text-xs outline-none focus:border-neutral-500"
      />
      <button
        type="submit"
        disabled={registerPayment.isPending}
        className="text-xs rounded-lg bg-neutral-100 text-neutral-900 px-3 py-1.5 font-medium disabled:opacity-50"
      >
        Receber
      </button>
    </form>
  );
}
