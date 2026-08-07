// Tipos compartilhados espelhando os DTOs do backend (Spring Boot).

export interface LoggedUser {
  publicId: string;
  email: string;
  profileName: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
  user: LoggedUser;
}

export interface UnitResponse {
  id: number;
  restaurantId: number;
  restaurantName: string;
  name: string;
  address: string;
  phone: string;
  timezone: string;
  active: boolean;
}

export interface CategoryResponse {
  id: number;
  unitId: number;
  name: string;
  displayOrder: number;
  active: boolean;
}

export interface ProductResponse {
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
}

export interface TableResponse {
  id: number;
  unitId: number;
  number: string;
  capacity: number;
  status: 'LIVRE' | 'OCUPADA' | 'RESERVADA' | 'AGUARDANDO_LIMPEZA' | 'BLOQUEADA';
}

export interface ServiceResponse {
  id: number;
  tableId: number;
  tableNumber: string;
  partySize: number;
  status: string;
  notes: string | null;
  openedAt: string;
  closedAt: string | null;
}

export interface CommandResponse {
  id: number;
  serviceId: number;
  status: string;
  openedAt: string;
  closedAt: string | null;
}

export interface OrderItemResponse {
  id: number;
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  notes: string | null;
  status: string;
  kitchenSectorId: number | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface OrderResponse {
  id: number;
  publicId: string;
  orderNumber: number;
  unitId: number;
  channel: string;
  commandId: number | null;
  status: string;
  subtotal: number;
  discount: number;
  serviceFee: number;
  deliveryFee: number;
  total: number;
  notes: string | null;
  createdAt: string;
  completedAt: string | null;
  items: OrderItemResponse[];
}

export interface OrderBalanceResponse {
  orderId: number;
  total: number;
  totalPaid: number;
  remaining: number;
  fullyPaid: boolean;
}

export interface KitchenSectorResponse {
  id: number;
  unitId: number;
  name: string;
}

export interface KitchenTaskResponse {
  orderItemId: number;
  orderId: number;
  orderNumber: number;
  productName: string;
  quantity: number;
  notes: string | null;
  status: string;
  startedAt: string | null;
  completedAt: string | null;
  orderCreatedAt: string;
}

export interface CashRegisterResponse {
  id: number;
  unitId: number;
  openingBalance: number;
  closingBalance: number | null;
  expectedBalance: number | null;
  difference: number | null;
  openedAt: string;
  closedAt: string | null;
  open: boolean;
}

export interface CashMovementResponse {
  id: number;
  type: string;
  amount: number;
  reason: string | null;
  createdAt: string;
}

export interface CustomerAddressResponse {
  id: number;
  label: string | null;
  street: string;
  number: string;
  complement: string | null;
  neighborhood: string;
  city: string;
  state: string;
  zipCode: string;
  referencePoint: string | null;
  isDefault: boolean;
}

export interface CustomerResponse {
  id: number;
  fullName: string;
  phone: string;
  email: string | null;
  document: string | null;
  birthDate: string | null;
  addresses: CustomerAddressResponse[];
}

export interface DeliveryResponse {
  id: number;
  orderId: number;
  courierId: number | null;
  addressSnapshot: string;
  neighborhoodSnapshot: string;
  fee: number;
  estimatedMinutes: number | null;
  status: string;
  receivedByName: string | null;
}
