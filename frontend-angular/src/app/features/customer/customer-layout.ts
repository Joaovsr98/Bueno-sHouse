import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CartService } from '../../core/cart.service';

@Component({
  selector: 'app-customer-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './customer-layout.html',
})
export class CustomerLayout {
  private readonly auth = inject(AuthService);
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);

  readonly cartCount = this.cart.count;

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
