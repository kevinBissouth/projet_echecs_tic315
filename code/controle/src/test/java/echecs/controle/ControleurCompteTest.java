package echecs.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.MotifFin;
import echecs.metier.joueur.CalculElo;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.EtatCompte;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.Cadence;
import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.PartieEntreJoueurs;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ControleurCompteTest {
    private static final String JETON_VALIDE = "jeton-alice";
    private static final Cadence RAPIDE = new Cadence(Duration.ofMinutes(10), Duration.ofSeconds(5), CategorieCadence.RAPIDE);

    private final ServiceAuthentification authentification = jeton -> {
        if (!JETON_VALIDE.equals(jeton)) {
            throw new ConnexionRefuseeException("Jeton invalide");
        }
        return "alice@exemple.com";
    };
    private final DepotsEnMemoire.Joueurs depotJoueurs = new DepotsEnMemoire.Joueurs();
    private final DepotsEnMemoire.Parties depotParties = new DepotsEnMemoire.Parties();
    private final FilesDeParties files = new FilesDeParties();
    private final ControleurCompte controleur = new ControleurCompte(authentification, depotJoueurs, depotParties, files);

    private ControleurPartie controleurPartie(Joueur joueur) {
        return new ControleurPartie(joueur, partie -> { }, new FauxMoteur(), new CalculElo(), depotParties, depotJoueurs,
                files, Runnable::run, Clock.systemUTC());
    }

    @Test
    void creerUnCompteLEnregistre() {
        Joueur alice = controleur.creerCompte("alice", "alice@exemple.com");

        assertEquals(List.of(alice), controleur.consulterComptes());
        assertTrue(alice.peutJouer());
    }

    @Test
    void unPseudoDejaPrisEstRefuse_RG12() {
        controleur.creerCompte("alice", "alice@exemple.com");

        assertThrows(CompteRefuseException.class, () -> controleur.creerCompte("alice", "autre@exemple.com"));
    }

    @Test
    void uneAdresseDejaPriseEstRefusee_RG12() {
        controleur.creerCompte("alice", "alice@exemple.com");

        assertThrows(CompteRefuseException.class, () -> controleur.creerCompte("autre", "alice@exemple.com"));
    }

    @Test
    void seConnecterRetrouveLeJoueurDeCetteIdentite() {
        Joueur alice = controleur.creerCompte("alice", "alice@exemple.com");

        assertEquals(alice, controleur.seConnecter(JETON_VALIDE));
    }

    @Test
    void unJetonInvalideEstRefuse() {
        controleur.creerCompte("alice", "alice@exemple.com");

        assertThrows(ConnexionRefuseeException.class, () -> controleur.seConnecter("faux"));
    }

    @Test
    void uneIdentiteSansCompteEstRefusee_BF2() {
        assertThrows(ConnexionRefuseeException.class, () -> controleur.seConnecter(JETON_VALIDE));
    }

    @Test
    void lesClassementsCouvrentLesTroisFamilles_RG6() {
        Joueur alice = controleur.creerCompte("alice", "alice@exemple.com");

        Map<CategorieCadence, Classement> classements = controleur.consulterClassements(alice);

        assertEquals(3, classements.size());
        assertEquals(Classement.POINTS_INITIAUX, classements.get(CategorieCadence.BLITZ).points());
    }

    @Test
    void suspendreConserveLeCompte_RG14() {
        Joueur alice = controleur.creerCompte("alice", "alice@exemple.com");

        controleur.suspendre(alice);

        assertEquals(EtatCompte.SUSPENDU, alice.etat());
        assertEquals(List.of(alice), controleur.consulterComptes());
    }

    @Test
    void suspendreUnCreateurAnnuleSaPartieEnAttente_RG8() {
        Joueur alice = controleur.creerCompte("alice", "alice@exemple.com");
        PartieEntreJoueurs partie = controleurPartie(alice).creerPartie(RAPIDE, Couleur.BLANC, null);

        controleur.suspendre(alice);

        assertEquals(EtatPartie.ANNULEE, partie.etat());
        assertFalse(alice.peutJouer());
    }

    @Test
    void suspendreUnJoueurEnPartieLuiFaitAbandonner_RG8() {
        Joueur alice = controleur.creerCompte("alice", "alice@exemple.com");
        Joueur bob = controleur.creerCompte("bob", "bob@exemple.com");
        PartieEntreJoueurs partie = controleurPartie(alice).creerPartie(RAPIDE, Couleur.BLANC, null);
        controleurPartie(bob).rejoindrePartie(partie);

        controleur.suspendre(bob);

        assertEquals(Optional.of(MotifFin.ABANDON), partie.motifFin());
        assertTrue(alice.peutJouer());
    }

    @Test
    void reactiverRendLeCompteJouable_RG8() {
        Joueur alice = controleur.creerCompte("alice", "alice@exemple.com");
        controleur.suspendre(alice);

        controleur.reactiver(alice);

        assertTrue(alice.peutJouer());
    }

    @Test
    void lesChangementsDeCompteSontEnregistres() {
        Joueur alice = controleur.creerCompte("alice", "alice@exemple.com");
        int avant = depotJoueurs.enregistrements;

        controleur.suspendre(alice);
        controleur.reactiver(alice);

        assertEquals(avant + 2, depotJoueurs.enregistrements);
    }
}
