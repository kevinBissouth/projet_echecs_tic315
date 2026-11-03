package echecs.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import echecs.metier.jeu.MotifFin;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.partie.Resultat;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ControleurHistoriqueTest {
    private static final PartieArchivee PARTIE = new PartieArchivee(UUID.randomUUID(),
            new PartieArchivee.Camp("alice", 1509, 9), new PartieArchivee.Camp("bob", 1491, -9), null, "5+0",
            CategorieCadence.BLITZ, Resultat.VICTOIRE_BLANCS, MotifFin.MAT, "8/8/8/8/8/8/8/8 w - - 0 1", 0, Instant.EPOCH, List.of());

    private final ControleurHistorique controleur = new ControleurHistorique(new ArchivesParties() {
        @Override
        public List<PartieArchivee> dernieresPartiesDe(String pseudo, int nombre) {
            return List.of(PARTIE);
        }

        @Override
        public Optional<PartieArchivee> trouver(UUID id) {
            return Optional.of(PARTIE).filter(partie -> partie.id().equals(id));
        }

        @Override
        public List<BilanCadence> bilansDe(String pseudo) {
            return List.of();
        }
    });

    @Test
    void unParticipantRevoitSaPartie_RG7() {
        assertEquals(PARTIE, controleur.revoir("bob", PARTIE.id()));
    }

    @Test
    void unAutreJoueurNePeutPasLaRevoir_RG7() {
        assertThrows(ActionRefuseeException.class, () -> controleur.revoir("carla", PARTIE.id()));
    }

    @Test
    void unePartieInconnueEstRefusee() {
        assertThrows(ActionRefuseeException.class, () -> controleur.revoir("alice", UUID.randomUUID()));
    }
}
