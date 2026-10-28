import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { UnitResponse } from '../../core/models';
import { SCREEN_ROLES, hasRole } from '../../core/role.guard';
import { Logo } from '../../shared/logo';
import { Icon } from '../../shared/icon';

const NAV_ITEMS = [
  { to: '/pedidos', label: 'Pedidos', icon: 'receipt' },
  { to: '/mesas', label: 'Mesas', icon: 'tables' },
  { to: '/cozinha', label: 'Cozinha', icon: 'flame' },
  { to: '/caixa', label: 'Caixa', icon: 'wallet' },
  { to: '/catalogo', label: 'Catálogo', icon: 'book' },
  { to: '/motoboy', label: 'Motoboy', icon: 'bike' },
  { to: '/estoque', label: 'Estoque', icon: 'package' },
  { to: '/fornecedores', label: 'Fornecedores e compras', icon: 'truck' },
  { to: '/fichas-tecnicas', label: 'Fichas técnicas', icon: 'clipboard' },
  { to: '/dashboard', label: 'Dashboard', icon: 'chart' },
  { to: '/usuarios', label: 'Usuários', icon: 'users' },
];

@Component({
  selector: 'app-staff-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Logo, Icon],
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
  /** Menu lateral aberto no celular. */
  menuOpen = signal(false);

  readonly profileLabels: Record<string, string> = {
    ADMINISTRADOR: 'Administrador', GERENTE: 'Gerente', CAIXA: 'Caixa',
    GARCOM: 'Garçom', COZINHA: 'Cozinha', MOTOBOY: 'Motoboy',
  };

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
