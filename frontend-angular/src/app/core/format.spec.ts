import { waitingLabel } from './format';

describe('format', () => {
  it('conta ha quanto tempo a entrega espera na fila', () => {
    const now = Date.parse('2026-11-03T12:00:00Z');
    expect(waitingLabel('2026-11-03T11:59:40Z', now)).toBe('acabou de ficar pronta');
    expect(waitingLabel('2026-11-03T11:48:00Z', now)).toBe('esperando há 12 min');
    expect(waitingLabel('2026-11-03T10:55:00Z', now)).toBe('esperando há 1 h 05');
    expect(waitingLabel(null, now)).toBeNull();
  });
});
