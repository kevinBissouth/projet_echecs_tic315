package echecs.metier.partie;

import static echecs.metier.partie.Jeu.jouer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import echecs.metier.jeu.Couleur;
import echecs.metier.joueur.CalculAPointsFixes;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.Joueur;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EtatsPartieTest {
    private static final Cadence BLITZ = new Cadence(Duration.ofMinutes(5), Duration.ZERO, CategorieCadence.BLITZ);

    private final HorlogeDeTest horloge = new HorlogeDeTest();
    private final Joueur alice = new Joueur("alice", "alice@exemple.com");
    private final Joueur bob = new Joueur("bob", "bob@exemple.com");
    private final Joueur carla = new Joueur("carla", "carla@exemple.com");
    private final PartieEntreJoueurs partie =
            new PartieEntreJoueurs(alice, BLITZ, Couleur.BLANC, null, new CalculAPointsFixes(), horloge);

    @Test
    void enAttenteOnNePeutNiJouerNiAbandonnerNiProposerLaNulle() {
        assertThrows(TransitionInterditeException.class, () -> jouer(partie, alice, "e2", "e4"));
        assertThrows(TransitionInterditeException.class, () -> partie.abandonner(alice));
        assertThrows(TransitionInterditeException.class, () -> partie.proposerNulle(alice));
    }

    @Test
    void enAttenteSeulLeCreateurPeutAnnuler_RG13() {
        assertThrows(TransitionInterditeException.class, () -> partie.annuler(bob));

        partie.annuler(alice);

        assertEquals(EtatPartie.ANNULEE, partie.etat());
        assertTrue(alice.peutJouer());
    }

    @Test
    void leSystemePeutAnnulerUnePartieEnAttenteDontLeCreateurEstSuspendu_RG8() {
        alice.suspendre();

        partie.annulerParLeSysteme();

        assertEquals(EtatPartie.ANNULEE, partie.etat());
    }

    @Test
    void uneFoisEnCoursOnNePeutPlusRejoindre() {
        partie.rejoindre(bob);

        assertThrows(TransitionInterditeException.class, () -> partie.rejoindre(carla));
    }

    @Test
    void annulerRestePossibleTantQueChaqueCampNAPasJoue_RG16() {
        partie.rejoindre(bob);
        jouer(partie, alice, "e2", "e4");

        partie.annuler(bob);

        assertEquals(EtatPartie.ANNULEE, partie.etat());
        assertTrue(alice.peutJouer());
        assertTrue(bob.peutJouer());
    }

    @Test
    void apresLePremierCoupDeChaqueCampLAnnulationEstRefusee_RG16() {
        partie.rejoindre(bob);
        jouer(partie, alice, "e2", "e4");
        jouer(partie, bob, "e7", "e5");

        assertThrows(TransitionInterditeException.class, () -> partie.annuler(alice));
    }

    @Test
    void unNonParticipantNePeutPasAnnuler() {
        partie.rejoindre(bob);

        assertThrows(TransitionInterditeException.class, () -> partie.annuler(carla));
    }

    @Test
    void unePartieAnnuleeNaNiResultatNiEffetSurLeClassement_RG15() {
        partie.rejoindre(bob);

        partie.annuler(alice);

        assertEquals(Optional.empty(), partie.resultat());
        assertEquals(Optional.empty(), partie.motifFin());
        assertEquals(Classement.POINTS_INITIAUX, alice.classement(CategorieCadence.BLITZ).points());
    }

    @Test
    void uneFoisAnnuleeLaPartieRefuseTout() {
        partie.annuler(alice);

        assertThrows(TransitionInterditeException.class, () -> partie.rejoindre(bob));
        assertThrows(TransitionInterditeException.class, () -> partie.annuler(alice));
        assertThrows(TransitionInterditeException.class, () -> partie.annulerParLeSysteme());
        assertThrows(TransitionInterditeException.class, () -> jouer(partie, alice, "e2", "e4"));
    }

    @Test
    void uneFoisTermineeLaPartieRefuseTout() {
        partie.rejoindre(bob);
        partie.abandonner(alice);

        assertThrows(TransitionInterditeException.class, () -> partie.abandonner(bob));
        assertThrows(TransitionInterditeException.class, () -> partie.annuler(alice));
        assertThrows(TransitionInterditeException.class, () -> partie.proposerNulle(bob));
        assertThrows(TransitionInterditeException.class, () -> jouer(partie, alice, "e2", "e4"));
    }
}
