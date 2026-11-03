/** Formata um numero como moeda brasileira (R$). */
export function currency(value: number): string {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

/** Minutos decorridos desde um instante ISO (>= 0, arredondado). */
export function elapsedMinutes(iso: string): number {
  return Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
}

/** "esperando ha 12 min" / "esperando ha 1 h 05" desde um instante ISO; null sem instante. */
export function waitingLabel(iso: string | null, now = Date.now()): string | null {
  if (!iso) return null;
  const min = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 60000));
  if (min < 1) return 'acabou de ficar pronta';
  if (min < 60) return `esperando há ${min} min`;
  return `esperando há ${Math.floor(min / 60)} h ${String(min % 60).padStart(2, '0')}`;
}
