package echecs.metier.partie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import echecs.metier.jeu.Couleur;
import echecs.metier.joueur.Joueur;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParticipationTest {
    private static final Instant DEBUT = Instant.parse("2026-10-16T20:00:00Z");
    private static final Duration UNE_MINUTE = Duration.ofMinutes(1);

    private final Participation participation =
            new Participation(new Joueur("alice", "alice@exemple.com"), Couleur.BLANC, UNE_MINUTE);

    @Test
    void lePenduleArreteNeCoulePas_RG5() {
        assertFalse(participation.tempsEcoule(DEBUT.plus(Duration.ofHours(1))));
    }

    @Test
    void leTempsEstEcouleQuandIlAtteintZero_RG5() {
        participation.demarrerPendule(DEBUT);

        assertFalse(participation.tempsEcoule(DEBUT.plus(UNE_MINUTE).minusMillis(1)));
        assertTrue(participation.tempsEcoule(DEBUT.plus(UNE_MINUTE)));
    }

    @Test
    void arreterLePendulRetireLeTempsPasseEtAjouteLIncrement_RG5() {
        participation.demarrerPendule(DEBUT);

        participation.arreterPendule(DEBUT.plusSeconds(20), Duration.ofSeconds(2));

        assertEquals(Optional.of(Duration.ofSeconds(42)), participation.tempsRestant());
    }
}
