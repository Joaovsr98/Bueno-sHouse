import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { UnitResponse } from '../../core/models';
import { SCREEN_ROLES, hasRole } from '../../core/role.guard';

const NAV_ITEMS = [
  { to: '/pedidos', label: 'Pedidos', icon: '🧾' },
  { to: '/mesas', label: 'Mesas', icon: '🍽️' },
  { to: '/cozinha', label: 'Cozinha', icon: '👨‍🍳' },
  { to: '/caixa', label: 'Caixa', icon: '💳' },
  { to: '/catalogo', label: 'Catálogo', icon: '📖' },
  { to: '/motoboy', label: 'Motoboy', icon: '🛵' },
  { to: '/estoque', label: 'Estoque', icon: '🥫' },
  { to: '/fornecedores', label: 'Fornecedores e compras', icon: '📦' },
  { to: '/fichas-tecnicas', label: 'Fichas técnicas', icon: '📋' },
  { to: '/dashboard', label: 'Dashboard', icon: '📊' },
  { to: '/usuarios', label: 'Usuários', icon: '👤' },
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

  readonly user = this.auth.user;
  /** Menu so com as telas que o perfil logado pode abrir (mesma regra do RoleGuard). */
  readonly navItems = computed(() =>
    NAV_ITEMS.filter((item) => hasRole(this.user()?.profileName, SCREEN_ROLES[item.to.slice(1)] ?? [])),
  );
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
