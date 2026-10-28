import { Component, input } from '@angular/core';

/**
 * Icones em linha fina (desenhos no estilo Lucide, 24x24, traco 1.8), no lugar
 * dos emojis. Cor herdada do texto (currentColor).
 */
const PATHS: Record<string, string> = {
  receipt: 'M5 3v18l2.5-1.5L10 21l2-1.5L14 21l2.5-1.5L19 21V3l-2.5 1.5L14 3l-2 1.5L10 3 7.5 4.5zM9 8h6M9 12h6M9 16h4',
  tables: 'M4 9h16M6 9v10M18 9v10M3 5h18',
  flame: 'M12 3c3 4 6 6.5 6 10.5a6 6 0 0 1-12 0C6 10 8 8 9.5 6c.5 2.5 1.5 3.5 2.5 4 0-2.5-.5-4.5 0-7z',
  wallet: 'M4 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a1 1 0 0 1-1-1V7zm0 0 11-3v3M16 13.5h.01',
  book: 'M4 5a2 2 0 0 1 2-2h13v15H6a2 2 0 0 0-2 2V5zm0 15a2 2 0 0 0 2 2h13v-4',
  bike: 'M5.5 17.5m-3 0a3 3 0 1 0 6 0a3 3 0 1 0-6 0M18.5 17.5m-3 0a3 3 0 1 0 6 0a3 3 0 1 0-6 0M5.5 17.5 9 10h5l4.5 7.5M12 10l-1.5-3H8M15 6h2l1.5 4',
  package: 'M12 3 4 7v10l8 4 8-4V7zM4 7l8 4 8-4M12 11v10',
  truck: 'M3 6h11v10H3zM14 9h4l3 3v4h-7M7 18.5m-1.5 0a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0M17 18.5m-1.5 0a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0',
  clipboard: 'M9 4h6v3H9zM9 5H6a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1h-3M9 12h6M9 16h4',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21v-1a6 6 0 0 1 12 0v1M16 3.5a4 4 0 0 1 0 7.5M22 21v-1a6 6 0 0 0-4-5.6',
  shield: 'M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6zM9 12l2 2 4-4',
  utensils: 'M7 3v7a2 2 0 0 0 2 2v9M11 3v7M7 3v7M17 21V3c-2 1.5-3 4-3 7v3h3',
  chef: 'M7 14a4 4 0 1 1 1.5-7.7A4 4 0 0 1 16 6.2 4 4 0 1 1 17 14v6H7zM7 17h10',
  card: 'M3 6h18v12H3zM3 10h18M7 15h3',
  logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 8l-4 4 4 4M6 12h10',
  cart: 'M3 4h2.5l2 11h11l2-8H6.5M9 19.5m-1.5 0a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0M18 19.5m-1.5 0a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0',
  menu: 'M4 6h16M4 12h16M4 18h16',
  close: 'M6 6l12 12M18 6 6 18',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
  pin: 'M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21zM12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  eye: 'M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  'eye-off': 'M3 3l18 18M10.6 5.1A10.4 10.4 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3.2 4.1M6.6 6.6C3.9 8.4 2 12 2 12s3.6 7 10 7a9.6 9.6 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2',
  spark: 'M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6',
};

@Component({
  selector: 'app-icon',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      [attr.stroke-width]="stroke()" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" class="shrink-0">
      <path [attr.d]="d()" />
    </svg>
  `,
})
export class Icon {
  readonly name = input.required<string>();
  readonly size = input(18);
  readonly stroke = input(1.8);
  d(): string {
    return PATHS[this.name()] ?? PATHS['spark'];
  }
}
