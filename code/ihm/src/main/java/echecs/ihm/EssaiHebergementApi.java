package echecs.ihm;

import echecs.controle.MoteurEchecs;
import echecs.controle.MoteurIndisponibleException;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Temporary: measures what the host can do. To be removed once the host is validated. */
@RestController
class EssaiHebergementApi {
    private static final String POSITION_INITIALE = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final int FORCE = 5;
    private static final int OCTETS_PAR_MO = 1024 * 1024;

    private final MoteurEchecs moteur;
    // One engine at a time: this public test page must not let anyone overload the server.
    private final ReentrantLock unSeulCalcul = new ReentrantLock();

    EssaiHebergementApi(MoteurEchecs moteur) {
        this.moteur = moteur;
    }

    @GetMapping("/api/essai/sante")
    Map<String, Object> sante() {
        Runtime jvm = Runtime.getRuntime();
        return Map.of(
                "memoireUtiliseeMo", (jvm.totalMemory() - jvm.freeMemory()) / OCTETS_PAR_MO,
                "memoireMaximaleMo", jvm.maxMemory() / OCTETS_PAR_MO,
                "processeurs", jvm.availableProcessors(),
                "demarrageSecondes", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
    }

    @PostMapping("/api/essai/moteur")
    Map<String, Object> essayerLeMoteur() {
        if (!unSeulCalcul.tryLock()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Un calcul est déjà en cours");
        }
        try {
            Instant debut = Instant.now();
            String coup = moteur.calculerCoup(POSITION_INITIALE, FORCE);
            return Map.of("coup", coup, "dureeMillisecondes", Duration.between(debut, Instant.now()).toMillis());
        } catch (MoteurIndisponibleException echec) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, echec.getMessage());
        } finally {
            unSeulCalcul.unlock();
        }
    }
}
