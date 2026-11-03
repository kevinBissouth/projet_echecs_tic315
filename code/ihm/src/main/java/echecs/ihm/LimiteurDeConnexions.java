package echecs.ihm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Slows down password guessing: after a few failures, this identifier must wait. */
@Component
public class LimiteurDeConnexions {
    private static final int ECHECS_PERMIS = 5;
    private static final Duration ATTENTE = Duration.ofMinutes(1);

    private record Echecs(int nombre, Instant dernier) { }

    private final Map<String, Echecs> echecs = new ConcurrentHashMap<>();
    private final Clock horloge;

    public LimiteurDeConnexions(Clock horloge) {
        this.horloge = horloge;
    }

    public boolean estBloque(String identifiant) {
        Echecs connus = echecs.get(cle(identifiant));
        return connus != null && connus.nombre() >= ECHECS_PERMIS && !estPerime(connus);
    }

    public void signalerEchec(String identifiant) {
        // Old entries are dropped here: anyone can send any identifier, memory must not grow with them.
        echecs.values().removeIf(this::estPerime);
        echecs.merge(cle(identifiant), new Echecs(1, horloge.instant()),
                (connus, nouveau) -> new Echecs(connus.nombre() + 1, nouveau.dernier()));
    }

    public void signalerReussite(String identifiant) {
        echecs.remove(cle(identifiant));
    }

    private boolean estPerime(Echecs connus) {
        return connus.dernier().plus(ATTENTE).isBefore(horloge.instant());
    }

    private static String cle(String identifiant) {
        return String.valueOf(identifiant).trim().toLowerCase(Locale.ROOT);
    }
}
