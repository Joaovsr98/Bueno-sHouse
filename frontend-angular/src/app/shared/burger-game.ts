import { Component, ElementRef, OnDestroy, signal, viewChild } from '@angular/core';

/** Ingrediente que cai: cor, altura da camada no prato e se vale ponto ou tira vida. */
interface Kind {
  name: string;
  color: string;
  layer: number;
  points: number;
  bad?: boolean;
}

const INGREDIENTS: Kind[] = [
  { name: 'Carne', color: '#6b3418', layer: 12, points: 10 },
  { name: 'Queijo', color: '#f5b544', layer: 6, points: 10 },
  { name: 'Alface', color: '#7bc96f', layer: 6, points: 10 },
  { name: 'Tomate', color: '#e5483b', layer: 6, points: 10 },
  { name: 'Bacon', color: '#c2553a', layer: 5, points: 15 },
  { name: 'Pão', color: '#e8952f', layer: 10, points: 10 },
];
const PEPPER: Kind = { name: 'Pimenta', color: '#ff2d2d', layer: 0, points: 0, bad: true };

/** Camadas que formam um burger completo (bonus e o prato esvazia). */
const BURGER_SIZE = 6;
const LIVES = 3;
const BEST_KEY = 'bueno.game.best';

interface Falling { kind: Kind; x: number; y: number; vy: number; }

/**
 * "Monte o Burger": minijogo para o cliente passar o tempo enquanto o pedido fica
 * pronto. Os ingredientes caem e o prato (mouse, dedo ou setas) pega: cada 6 camadas
 * fecham um burger com bonus; pegar pimenta tira uma vida. Tudo desenhado em canvas,
 * sem imagens externas; o recorde fica so no navegador da pessoa.
 */
@Component({
  selector: 'app-burger-game',
  template: `
    <div class="relative rounded-2xl overflow-hidden border border-white/10 bg-ink-900 select-none" #box>
      <canvas #canvas class="block w-full touch-none" (pointermove)="onPointer($event)" (pointerdown)="onPointer($event)"></canvas>

      <div class="absolute top-3 left-4 right-4 flex items-center justify-between text-xs font-mono pointer-events-none">
        <span class="text-neutral-300">Pontos <strong class="text-brand-300 text-sm">{{ score() }}</strong></span>
        <span class="text-neutral-500">Recorde {{ best() }}</span>
        <span class="text-red-400 tracking-widest" aria-label="Vidas">{{ '♥'.repeat(lives()) }}<span class="text-neutral-700">{{ '♥'.repeat(livesLost()) }}</span></span>
      </div>

      @if (state() !== 'playing') {
        <div class="absolute inset-0 grid place-items-center bg-ink-950/75 backdrop-blur-[2px] text-center p-6">
          <div class="space-y-3 max-w-xs">
            @if (state() === 'over') {
              <p class="eyebrow-plain text-[0.65rem]">Fim de jogo</p>
              <p class="font-display text-4xl font-bold">{{ score() }} <span class="text-base text-neutral-400">pontos</span></p>
              @if (newRecord()) { <p class="text-sm text-brand-300">Novo recorde!</p> }
            } @else {
              <p class="font-display text-2xl font-bold uppercase">Monte o <span class="text-ember">burger</span></p>
              <p class="text-sm text-neutral-400 leading-relaxed">
                Pegue os ingredientes com o prato. 6 camadas fecham um burger e valem bônus. Fuja da pimenta!
              </p>
              <p class="text-xs text-neutral-500">Mouse, dedo ou setas do teclado.</p>
            }
            <button (click)="start()" class="btn-brand rounded-xl px-5 py-2.5 text-sm">
              {{ state() === 'over' ? 'Jogar de novo' : 'Começar' }}
            </button>
          </div>
        </div>
      }
    </div>
  `,
})
export class BurgerGame implements OnDestroy {
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly boxRef = viewChild.required<ElementRef<HTMLDivElement>>('box');

  readonly score = signal(0);
  readonly lives = signal(LIVES);
  readonly livesLost = () => LIVES - this.lives();
  readonly best = signal(this.readBest());
  readonly newRecord = signal(false);
  readonly state = signal<'ready' | 'playing' | 'over'>('ready');

  private ctx!: CanvasRenderingContext2D;
  private width = 600;
  private readonly height = 360;
  private plateX = 300;
  private keys = { left: false, right: false };
  private items: Falling[] = [];
  private stack: Kind[] = [];
  private spawnIn = 0;
  private lastTime = 0;
  private frame = 0;
  private flash = 0;
  private resizeObserver?: ResizeObserver;

  private readonly onKeyDown = (e: KeyboardEvent) => this.key(e, true);
  private readonly onKeyUp = (e: KeyboardEvent) => this.key(e, false);
  private readonly onVisibility = () => { if (document.hidden && this.state() === 'playing') this.pause(); };

  constructor() {
    queueMicrotask(() => this.setup());
  }

  private setup(): void {
    const canvas = this.canvasRef().nativeElement;
    this.ctx = canvas.getContext('2d')!;
    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.boxRef().nativeElement);
    this.resize();
    window.addEventListener('keydown', this.onKeyDown);
    window.addEventListener('keyup', this.onKeyUp);
    document.addEventListener('visibilitychange', this.onVisibility);
    this.draw();
  }

  private resize(): void {
    const canvas = this.canvasRef().nativeElement;
    const ratio = window.devicePixelRatio || 1;
    this.width = this.boxRef().nativeElement.clientWidth || 600;
    canvas.width = this.width * ratio;
    canvas.height = this.height * ratio;
    canvas.style.height = `${this.height}px`;
    this.ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    this.plateX = Math.min(Math.max(this.plateX, 50), this.width - 50);
    if (this.state() !== 'playing') this.draw();
  }

  start(): void {
    this.score.set(0);
    this.lives.set(LIVES);
    this.newRecord.set(false);
    this.items = [];
    this.stack = [];
    this.spawnIn = 0;
    this.plateX = this.width / 2;
    this.state.set('playing');
    this.lastTime = performance.now();
    this.frame = requestAnimationFrame((t) => this.loop(t));
  }

  private pause(): void {
    cancelAnimationFrame(this.frame);
    this.state.set('ready');
  }

  onPointer(e: PointerEvent): void {
    const rect = this.canvasRef().nativeElement.getBoundingClientRect();
    this.plateX = Math.min(Math.max(e.clientX - rect.left, 50), this.width - 50);
  }

  private key(e: KeyboardEvent, down: boolean): void {
    if (this.state() !== 'playing') return;
    if (e.key === 'ArrowLeft') this.keys.left = down;
    else if (e.key === 'ArrowRight') this.keys.right = down;
    else return;
    e.preventDefault();
  }

  /** Dificuldade sobe com os pontos: mais rapido e com mais pimenta. */
  private level(): number {
    return 1 + this.score() / 250;
  }

  private loop(time: number): void {
    const dt = Math.min((time - this.lastTime) / 1000, 0.05);
    this.lastTime = time;
    this.update(dt);
    this.draw();
    if (this.state() === 'playing') this.frame = requestAnimationFrame((t) => this.loop(t));
  }

  private update(dt: number): void {
    const speed = 420 * dt;
    if (this.keys.left) this.plateX = Math.max(50, this.plateX - speed);
    if (this.keys.right) this.plateX = Math.min(this.width - 50, this.plateX + speed);

    this.spawnIn -= dt;
    if (this.spawnIn <= 0) {
      const level = this.level();
      const pepperChance = Math.min(0.15 + level * 0.05, 0.4);
      const kind = Math.random() < pepperChance ? PEPPER : INGREDIENTS[Math.floor(Math.random() * INGREDIENTS.length)];
      this.items.push({ kind, x: 30 + Math.random() * (this.width - 60), y: -20, vy: 110 + level * 45 + Math.random() * 40 });
      this.spawnIn = Math.max(0.35, 1.1 - level * 0.12);
    }

    const plateTop = this.height - 34 - this.stackHeight();
    for (const item of this.items) {
      item.y += item.vy * dt;
      const caught = item.y >= plateTop - 8 && item.y <= plateTop + 14 && Math.abs(item.x - this.plateX) < 52;
      if (caught) {
        item.y = Infinity;
        this.catch(item.kind);
      }
    }
    this.items = this.items.filter((i) => i.y < this.height + 30);
    if (this.flash > 0) this.flash -= dt;
  }

  private catch(kind: Kind): void {
    if (kind.bad) {
      this.flash = 0.35;
      this.lives.update((l) => l - 1);
      if (this.lives() <= 0) this.gameOver();
      return;
    }
    this.score.update((s) => s + kind.points);
    this.stack.push(kind);
    if (this.stack.length >= BURGER_SIZE) {
      this.score.update((s) => s + 50);
      this.stack = [];
    }
  }

  private gameOver(): void {
    this.state.set('over');
    cancelAnimationFrame(this.frame);
    if (this.score() > this.best()) {
      this.best.set(this.score());
      this.newRecord.set(true);
      try { localStorage.setItem(BEST_KEY, String(this.score())); } catch { /* sem armazenamento: so nao guarda o recorde */ }
    }
  }

  private stackHeight(): number {
    return this.stack.reduce((h, k) => h + k.layer, 0);
  }

  // ---- desenho ----

  private draw(): void {
    const c = this.ctx;
    if (!c) return;
    const w = this.width;
    const h = this.height;
    const bg = c.createLinearGradient(0, 0, 0, h);
    bg.addColorStop(0, '#0c0e14');
    bg.addColorStop(1, '#1a0f0a');
    c.fillStyle = bg;
    c.fillRect(0, 0, w, h);
    // brilho da chapa no chao
    c.fillStyle = 'rgba(255,106,26,0.08)';
    c.fillRect(0, h - 18, w, 18);

    for (const item of this.items) this.drawItem(item.kind, item.x, item.y);
    this.drawPlate();

    if (this.flash > 0) {
      c.fillStyle = `rgba(255,45,45,${this.flash})`;
      c.fillRect(0, 0, w, h);
    }
  }

  private drawPlate(): void {
    const c = this.ctx;
    const x = this.plateX;
    const base = this.height - 22;
    // prato
    c.fillStyle = '#e9e4da';
    this.roundRect(x - 52, base, 104, 8, 4);
    c.fill();
    // pao de baixo
    c.fillStyle = '#e8952f';
    this.roundRect(x - 42, base - 12, 84, 12, 5);
    c.fill();
    // camadas empilhadas
    let y = base - 12;
    for (const kind of this.stack) {
      y -= kind.layer;
      c.fillStyle = kind.color;
      if (kind.name === 'Pão') {
        c.beginPath();
        c.ellipse(x, y + kind.layer, 44, kind.layer + 4, 0, Math.PI, 0);
        c.fill();
      } else {
        this.roundRect(x - 46, y, 92, kind.layer, kind.layer / 2);
        c.fill();
      }
    }
  }

  private drawItem(kind: Kind, x: number, y: number): void {
    const c = this.ctx;
    c.fillStyle = kind.color;
    if (kind.bad) {
      // pimenta: corpo curvo + cabinho verde
      c.beginPath();
      c.ellipse(x, y, 9, 18, 0.5, 0, Math.PI * 2);
      c.fill();
      c.fillStyle = '#3f9b3a';
      c.fillRect(x + 4, y - 22, 4, 8);
      return;
    }
    if (kind.name === 'Pão') {
      c.beginPath();
      c.ellipse(x, y + 6, 26, 16, 0, Math.PI, 0);
      c.fill();
      c.fillStyle = '#fff4dc';
      for (const [dx, dy] of [[-10, -2], [2, -6], [11, 0]]) c.fillRect(x + dx, y + dy, 3, 2);
      return;
    }
    if (kind.name === 'Tomate') {
      c.beginPath();
      c.ellipse(x, y, 22, 7, 0, 0, Math.PI * 2);
      c.fill();
      return;
    }
    this.roundRect(x - 26, y - kind.layer / 2, 52, Math.max(kind.layer, 6), 4);
    c.fill();
    if (kind.name === 'Alface') {
      c.fillStyle = '#5aa64f';
      for (let i = -22; i <= 18; i += 10) c.fillRect(x + i, y + 2, 6, 3);
    }
  }

  private roundRect(x: number, y: number, w: number, h: number, r: number): void {
    const c = this.ctx;
    c.beginPath();
    c.roundRect(x, y, w, h, r);
  }

  private readBest(): number {
    try { return Number(localStorage.getItem(BEST_KEY)) || 0; } catch { return 0; }
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.frame);
    this.resizeObserver?.disconnect();
    window.removeEventListener('keydown', this.onKeyDown);
    window.removeEventListener('keyup', this.onKeyUp);
    document.removeEventListener('visibilitychange', this.onVisibility);
  }
}
