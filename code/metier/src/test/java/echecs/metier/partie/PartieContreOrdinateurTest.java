package echecs.metier.partie;

import static echecs.metier.partie.Jeu.caseDe;
import static echecs.metier.partie.Jeu.jouer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import echecs.metier.jeu.Coup;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.MotifFin;
import echecs.metier.jeu.TypePromotion;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.Joueur;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PartieContreOrdinateurTest {
    private static final int FORCE = 5;
    private static final TypePromotion SANS_PROMOTION = null;

    private final HorlogeDeTest horloge = new HorlogeDeTest();
    private final Joueur alice = new Joueur("alice", "alice@exemple.com");
    private final PartieContreOrdinateur partie = new PartieContreOrdinateur(alice, Couleur.BLANC, FORCE, horloge);

    private Coup ordinateur(String depart, String arrivee) {
        return partie.jouerCoupOrdinateur(caseDe(partie.plateau(), depart), caseDe(partie.plateau(), arrivee), SANS_PROMOTION);
    }

    @Test
    void laPartieCommenceSansAdversaireAAttendre() {
        assertEquals(EtatPartie.EN_COURS, partie.etat());
        assertEquals(FORCE, partie.force());
        assertFalse(alice.peutJouer());
    }

    @Test
    void ilNYAPasDePendule_RG10() {
        assertEquals(Optional.empty(), partie.participations().getFirst().tempsRestant());
    }

    @Test
    void leJoueurEtLOrdinateurJouentChacunLeurTour_RG3() {
        assertFalse(partie.ordinateurALeTrait());
        assertThrows(CoupRefuseException.class, () -> ordinateur("e7", "e5"));

        jouer(partie, alice, "e2", "e4");

        assertTrue(partie.ordinateurALeTrait());
        assertThrows(CoupRefuseException.class, () -> jouer(partie, alice, "d2", "d4"));
        assertEquals("e5", ordinateur("e7", "e5").notation());
    }

    @Test
    void unJoueurAvecLesNoirsLaisseLOrdinateurCommencer_RG3() {
        Joueur bob = new Joueur("bob", "bob@exemple.com");
        PartieContreOrdinateur contreBob = new PartieContreOrdinateur(bob, Couleur.NOIR, FORCE, horloge);

        assertTrue(contreBob.ordinateurALeTrait());
        assertThrows(CoupRefuseException.class, () -> jouer(contreBob, bob, "e7", "e5"));
    }

    @Test
    void unCoupIllegalDuMoteurEstRefuse_RG1() {
        jouer(partie, alice, "e2", "e4");

        assertThrows(CoupRefuseException.class, () -> ordinateur("e7", "e4"));
        assertTrue(partie.ordinateurALeTrait());
    }

    @Test
    void unMatDeLOrdinateurFaitPerdreLeJoueurSansToucherSonClassement_RG6() {
        jouer(partie, alice, "f2", "f3");
        ordinateur("e7", "e5");
        jouer(partie, alice, "g2", "g4");
        ordinateur("d8", "h4");

        assertEquals(EtatPartie.TERMINEE, partie.etat());
        assertEquals(Optional.of(Resultat.VICTOIRE_NOIRS), partie.resultat());
        assertEquals(Optional.of(MotifFin.MAT), partie.motifFin());
        assertTrue(alice.peutJouer());
        for (CategorieCadence categorie : CategorieCadence.values()) {
            assertEquals(Classement.POINTS_INITIAUX, alice.classement(categorie).points());
        }
    }

    @Test
    void siLeMoteurNeRepondPlusLaPartieEstAnnulee_RG15() {
        jouer(partie, alice, "e2", "e4");
        passerLeDebutDePartie();

        partie.annulerParLeSysteme();

        assertEquals(EtatPartie.ANNULEE, partie.etat());
        assertEquals(Optional.empty(), partie.resultat());
    }

    private void passerLeDebutDePartie() {
        ordinateur("e7", "e5");
        jouer(partie, alice, "g1", "f3");
    }

    @Test
    void leJoueurPeutAbandonner_RG4() {
        partie.abandonner(alice);

        assertEquals(Optional.of(Resultat.VICTOIRE_NOIRS), partie.resultat());
        assertEquals(Optional.of(MotifFin.ABANDON), partie.motifFin());
    }
}
