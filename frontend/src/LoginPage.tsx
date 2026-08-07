import { useState } from 'react';

type LoginResponse = {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
  user: {
    publicId: string;
    email: string;
    profileName: string;
  };
};

type Props = {
  onLoginSuccess: (data: LoginResponse) => void;
};

const DEMO_PASSWORD = 'admin123';

const QUICK_ACCESS_PROFILES = [
  { email: 'admin@demo.local', label: 'Administrador' },
  { email: 'gerente@demo.local', label: 'Gerente' },
  { email: 'garcom@demo.local', label: 'Garçom' },
  { email: 'cozinha@demo.local', label: 'Cozinha' },
  { email: 'caixa@demo.local', label: 'Caixa' },
  { email: 'motoboy@demo.local', label: 'Motoboy' },
];

/**
 * Botoes de acesso rapido (ambiente de desenvolvimento): cada perfil de
 * demonstracao tem um usuario seed com a mesma senha (ver
 * V900__seed_demo_data.sql). Um clique preenche e envia o login sem exigir
 * digitacao - existe apenas para acelerar testes manuais entre perfis.
 */
export function LoginPage({ onLoginSuccess }: Props) {
  const [email, setEmail] = useState('admin@demo.local');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function doLogin(emailToUse: string, passwordToUse: string) {
    setError(null);
    setLoading(true);

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: emailToUse, password: passwordToUse }),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message || `Erro HTTP ${res.status}`);
      }

      const data: LoginResponse = await res.json();
      onLoginSuccess(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro desconhecido');
    } finally {
      setLoading(false);
    }
  }

  function handleQuickAccess(quickEmail: string) {
    setEmail(quickEmail);
    setPassword(DEMO_PASSWORD);
    doLogin(quickEmail, DEMO_PASSWORD);
  }

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex items-center justify-center p-6">
      <div className="max-w-sm w-full space-y-6">
        <div>
          <h1 className="text-xl font-semibold mb-1">Entrar</h1>
          <p className="text-sm text-neutral-400">Sistema de Gestão para Restaurante</p>
        </div>

        <div className="rounded-xl border border-neutral-800 bg-neutral-900 p-5 space-y-3">
          <p className="text-xs text-neutral-400">Acesso rápido (ambiente de demonstração)</p>
          <div className="grid grid-cols-2 gap-2">
            {QUICK_ACCESS_PROFILES.map((profile) => (
              <button
                key={profile.email}
                type="button"
                disabled={loading}
                onClick={() => handleQuickAccess(profile.email)}
                className="rounded-lg bg-neutral-800 border border-neutral-700 py-2 text-sm hover:bg-neutral-700 disabled:opacity-50"
              >
                {profile.label}
              </button>
            ))}
          </div>
        </div>

        <div className="flex items-center gap-3 text-xs text-neutral-600">
          <div className="flex-1 h-px bg-neutral-800" />
          ou entre manualmente
          <div className="flex-1 h-px bg-neutral-800" />
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            doLogin(email, password);
          }}
          className="rounded-xl border border-neutral-800 bg-neutral-900 p-8 space-y-4"
        >
          <div className="space-y-1">
            <label className="text-sm text-neutral-300">E-mail</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
              required
            />
          </div>

          <div className="space-y-1">
            <label className="text-sm text-neutral-300">Senha</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-lg bg-neutral-800 border border-neutral-700 px-3 py-2 text-sm outline-none focus:border-neutral-500"
              required
            />
          </div>

          {error && (
            <div className="rounded-lg bg-red-950 border border-red-800 p-3 text-sm text-red-300">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-lg bg-neutral-100 text-neutral-900 font-medium py-2 text-sm disabled:opacity-50"
          >
            {loading ? 'Entrando...' : 'Entrar'}
          </button>

          <p className="text-xs text-neutral-500">
            Ambiente de desenvolvimento: admin@demo.local / admin123
          </p>
        </form>
      </div>
    </div>
  );
}
