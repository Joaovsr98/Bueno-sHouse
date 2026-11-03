import { TestBed } from '@angular/core/testing';
import { CartService } from './cart.service';
import { ProductResponse } from './models';

const BURGER = { id: 1, name: 'X-Burguer Artesanal', basePrice: 28.9 } as ProductResponse;
const SODA = { id: 3, name: 'Refrigerante Lata 350ml', basePrice: 7 } as ProductResponse;

describe('CartService', () => {
  let cart: CartService;

  beforeEach(() => {
    localStorage.removeItem('bueno.cart');
    TestBed.configureTestingModule({});
    cart = TestBed.inject(CartService);
  });

  afterEach(() => localStorage.removeItem('bueno.cart'));

  it('soma a quantidade do mesmo produto em vez de duplicar a linha', () => {
    cart.add(BURGER);
    cart.add(BURGER, 2);
    cart.add(SODA);

    expect(cart.lines().length).toBe(2);
    expect(cart.count()).toBe(4);
    expect(cart.subtotal()).toBeCloseTo(28.9 * 3 + 7, 2);
  });

  it('avisa o ultimo item adicionado (o carrinho flutuante mostra "+2 ...")', () => {
    cart.add(BURGER, 2);
    expect(cart.lastAdded()?.text).toBe('+2 X-Burguer Artesanal');
  });

  it('tira a linha quando a quantidade chega a zero', () => {
    cart.add(SODA);
    cart.changeQty(SODA.id, -1);
    expect(cart.lines()).toEqual([]);
  });

  it('guarda a observacao do item', () => {
    cart.add(BURGER, 1, 'sem cebola');
    expect(cart.lines()[0].notes).toBe('sem cebola');
  });
});
