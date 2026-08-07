import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, apiErrorMessage } from '../../core/api.service';
import { ServiceResponse, CommandResponse } from '../../core/models';

const SERVICE_STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberto',
  IN_SERVICE: 'Em atendimento',
  AWAITING_CLOSURE: 'Aguardando encerramento',
  CLOSED: 'Encerrado',
  CANCELLED: 'Cancelado',
};

const SERVICE_TRANSITIONS: Record<string, { to: string; label: string }[]> = {
  OPEN: [{ to: 'IN_SERVICE', label: 'Marcar em atendimento' }],
  IN_SERVICE: [{ to: 'AWAITING_CLOSURE', label: 'Pedir encerramento' }],
  AWAITING_CLOSURE: [
    { to: 'CLOSED', label: 'Encerrar atendimento' },
    { to: 'IN_SERVICE', label: 'Voltar para atendimento' },
  ],
};

const COMMAND_STATUS_LABELS: Record<string, string> = {
  ABERTA: 'Aberta',
  EM_ATENDIMENTO: 'Em atendimento',
  AGUARDANDO_PAGAMENTO: 'Aguardando pagamento',
  PARCIALMENTE_PAGA: 'Parcialmente paga',
  FECHADA: 'Fechada',
  CANCELADA: 'Cancelada',
};

const COMMAND_TRANSITIONS: Record<string, { to: string; label: string }[]> = {
  ABERTA: [{ to: 'EM_ATENDIMENTO', label: 'Iniciar atendimento' }],
  EM_ATENDIMENTO: [{ to: 'AGUARDANDO_PAGAMENTO', label: 'Enviar para pagamento' }],
  AGUARDANDO_PAGAMENTO: [{ to: 'FECHADA', label: 'Marcar como fechada' }],
};

@Component({
  selector: 'app-service',
  imports: [],
  templateUrl: './service.html',
})
export class Service {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly serviceStatusLabels = SERVICE_STATUS_LABELS;
  readonly serviceTransitions = SERVICE_TRANSITIONS;
  readonly commandStatusLabels = COMMAND_STATUS_LABELS;
  readonly commandTransitions = COMMAND_TRANSITIONS;

  serviceId = this.route.snapshot.paramMap.get('serviceId')!;
  service = signal<ServiceResponse | null>(
    (history.state?.service as ServiceResponse | undefined) ?? null,
  );
  commands = signal<CommandResponse[]>([]);
  error = signal<string | null>(null);

  constructor() {
    this.loadCommands();
  }

  loadCommands(): void {
    this.api.get<CommandResponse[]>(`/commands?serviceId=${this.serviceId}`).subscribe({
      next: (c) => this.commands.set(c),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao carregar comandas')),
    });
  }

  serviceStatus(): string | undefined {
    return this.service()?.status;
  }

  openCommand(): void {
    this.api.post<CommandResponse>(`/commands?serviceId=${this.serviceId}`).subscribe({
      next: () => this.loadCommands(),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao abrir comanda')),
    });
  }

  transitionCommand(id: number, status: string): void {
    this.api.post<CommandResponse>(`/commands/${id}/transitions`, { status }).subscribe({
      next: () => this.loadCommands(),
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao alterar status da comanda')),
    });
  }

  transitionService(status: string): void {
    this.api.post<ServiceResponse>(`/services/${this.serviceId}/transitions`, { status }).subscribe({
      next: (updated) => {
        this.service.set(updated);
        if (updated.status === 'CLOSED') this.router.navigateByUrl('/mesas');
      },
      error: (e) => this.error.set(apiErrorMessage(e, 'Erro ao alterar status do atendimento')),
    });
  }

  commandActions(status: string): { to: string; label: string }[] {
    return this.commandTransitions[status] ?? [];
  }

  serviceActions(): { to: string; label: string }[] {
    const s = this.serviceStatus();
    return s ? this.serviceTransitions[s] ?? [] : [];
  }

  isCommandOpen(status: string): boolean {
    return !['FECHADA', 'CANCELADA'].includes(status);
  }

  goToOrder(commandId: number): void {
    this.router.navigate(['/atendimento', this.serviceId, 'comanda', commandId]);
  }

  back(): void {
    this.router.navigateByUrl('/mesas');
  }
}
