package com.restaurante.sistema.modules.demo;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.config.BootstrapAdminRunner;
import com.restaurante.sistema.config.DemoAdminRunner;
import jakarta.annotation.PreDestroy;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Volta o banco ao estado da demonstracao (cardapio, estoque, historico de vendas, pedidos e
 * usuarios de exemplo), para a apresentacao comecar limpa mesmo depois de muita gente testar
 * com o admin publico do guia. Apaga tudo e roda de novo as mesmas migrations e seeds do
 * primeiro deploy (Flyway clean + migrate) e recria os admins de inicializacao.
 *
 * So funciona com app.demo-reset.enabled=true (profile dev, o da demonstracao). Roda em
 * segundo plano: com o banco na nuvem leva perto de um minuto, mais que a espera da Vercel.
 */
@Service
public class DemoResetService {

    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);

    /** Situacao da restauracao (a tela acompanha sem login: durante ela nem a tabela de usuarios existe). */
    public record Status(boolean running, Instant finishedAt, String error) {}

    private final boolean enabled;
    private final Flyway flyway;
    private final BootstrapAdminRunner bootstrapAdmin;
    private final ObjectProvider<DemoAdminRunner> demoAdmin;
    private final CacheManager cacheManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Instant finishedAt;
    private volatile String error;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "restaurar-demonstracao");
        thread.setDaemon(true);
        return thread;
    });

    public DemoResetService(@Value("${app.demo-reset.enabled:false}") boolean enabled, Flyway flyway,
                            BootstrapAdminRunner bootstrapAdmin, ObjectProvider<DemoAdminRunner> demoAdmin,
                            CacheManager cacheManager) {
        this.enabled = enabled;
        this.flyway = flyway;
        this.bootstrapAdmin = bootstrapAdmin;
        this.demoAdmin = demoAdmin;
        this.cacheManager = cacheManager;
    }

    public Status status() {
        return new Status(running.get(), finishedAt, error);
    }

    /** Comeca em segundo plano; false = ja tem uma restauracao rodando. */
    public boolean start() {
        assertEnabled();
        if (!running.compareAndSet(false, true)) return false;
        worker.execute(() -> {
            try {
                restore();
            } catch (RuntimeException ignored) {
                // ja registrado em restore(); a tela ve o erro pelo status
            }
        });
        return true;
    }

    /** Restaura agora, na mesma thread (testes). */
    public void restoreNow() {
        assertEnabled();
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException("Ja existe uma restauracao em andamento");
        }
        restore();
    }

    private void restore() {
        try {
            log.warn("Restaurando a demonstracao: o banco sera apagado e recriado");
            flyway.clean();
            flyway.migrate();
            bootstrapAdmin.run(null);
            demoAdmin.ifAvailable(runner -> runner.run(null));
            cacheManager.getCacheNames().forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear));
            error = null;
            log.warn("Demonstracao restaurada");
        } catch (RuntimeException e) {
            error = e.getMessage();
            log.error("Falha ao restaurar a demonstracao", e);
            throw e;
        } finally {
            finishedAt = Instant.now();
            running.set(false);
        }
    }

    private void assertEnabled() {
        if (!enabled) throw new BusinessException("Restaurar a demonstracao esta desligado neste ambiente");
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
