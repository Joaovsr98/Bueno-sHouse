import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CartService } from '../../core/cart.service';
import { Logo } from '../../shared/logo';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'app-customer-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Logo, Icon],
  templateUrl: './customer-layout.html',
})
export class CustomerLayout {
  private readonly auth = inject(AuthService);
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);

  readonly cartCount = this.cart.count;
  readonly isLoggedIn = this.auth.isLoggedIn;
  readonly year = new Date().getFullYear();

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/');
  }
}
