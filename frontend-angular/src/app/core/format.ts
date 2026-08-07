/** Formata um numero como moeda brasileira (R$). */
export function currency(value: number): string {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

/** Minutos decorridos desde um instante ISO (>= 0, arredondado). */
export function elapsedMinutes(iso: string): number {
  return Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
}
