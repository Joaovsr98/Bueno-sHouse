import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { UnitResponse } from '../../core/models';

const NAV_ITEMS = [
  { to: '/mesas', label: 'Mesas', icon: '🍽️' },
  { to: '/cozinha', label: 'Cozinha', icon: '👨‍🍳' },
  { to: '/caixa', label: 'Caixa', icon: '💳' },
  { to: '/catalogo', label: 'Catálogo', icon: '📖' },
  { to: '/motoboy', label: 'Motoboy', icon: '🛵' },
];

@Component({
  selector: 'app-staff-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './staff-layout.html',
})
export class StaffLayout implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly navItems = NAV_ITEMS;
  readonly user = this.auth.user;
  readonly unitId = this.auth.unitId;
  units = signal<UnitResponse[]>([]);

  ngOnInit(): void {
    this.api.get<UnitResponse[]>('/units').subscribe({
      next: (units) => {
        this.units.set(units);
        if (this.unitId() == null && units.length > 0) {
          this.auth.setUnitId(units[0].id);
        }
      },
      error: () => this.units.set([]),
    });
  }

  onUnitChange(value: string): void {
    this.auth.setUnitId(Number(value));
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
