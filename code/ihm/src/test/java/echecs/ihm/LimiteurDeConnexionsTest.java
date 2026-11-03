package echecs.ihm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LimiteurDeConnexionsTest {
    private static final class HorlogeReglable extends Clock {
        private Instant maintenant = Instant.EPOCH;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    private final HorlogeReglable horloge = new HorlogeReglable();
    private final LimiteurDeConnexions limiteur = new LimiteurDeConnexions(horloge);

    private void echouer(int fois) {
        for (int tentative = 0; tentative < fois; tentative++) {
            limiteur.signalerEchec("Alice");
        }
    }

    @Test
    void quatreEchecsNeBloquentPas() {
        echouer(4);

        assertFalse(limiteur.estBloque("alice"));
    }

    @Test
    void cinqEchecsBloquentSansTenirCompteDesMajuscules() {
        echouer(5);

        assertTrue(limiteur.estBloque("ALICE"));
        assertFalse(limiteur.estBloque("bob"));
    }

    @Test
    void leBlocageSeLeveApresUneMinute() {
        echouer(5);

        horloge.maintenant = horloge.maintenant.plus(Duration.ofSeconds(61));

        assertFalse(limiteur.estBloque("alice"));
    }

    @Test
    void uneReussiteEffaceLesEchecs() {
        echouer(4);

        limiteur.signalerReussite("alice");
        echouer(1);

        assertFalse(limiteur.estBloque("alice"));
    }
}
