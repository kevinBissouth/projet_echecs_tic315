package echecs.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.MotifFin;
import echecs.metier.joueur.CalculElo;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.Cadence;
import echecs.metier.partie.CoupRefuseException;
import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.ObservateurPartie;
import echecs.metier.partie.Partie;
import echecs.metier.partie.PartieEntreJoueurs;
import echecs.metier.partie.Resultat;
import echecs.metier.partie.TransitionInterditeException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ControleurPartieTest {
    private static final Cadence RAPIDE = new Cadence(Duration.ofMinutes(10), Duration.ofSeconds(5), CategorieCadence.RAPIDE);
    private static final int FORCE = 5;
    private static final int LIMITE_SECONDES = 10;
    // Runs the engine at once: a test then has no thread to wait for.
    private static final Executor IMMEDIAT = Runnable::run;

    private final DepotsEnMemoire.Joueurs depotJoueurs = new DepotsEnMemoire.Joueurs();
    private final DepotsEnMemoire.Parties depotParties = new DepotsEnMemoire.Parties();
    private final FilesDeParties files = new FilesDeParties();
    private final Joueur alice = new Joueur("alice", "alice@exemple.com");
    private final Joueur bob = new Joueur("bob", "bob@exemple.com");
    private final List<String> ecranDeBob = new ArrayList<>();
    private final ControleurPartie controleurAlice = controleur(alice, partie -> { }, new FauxMoteur());
    private final ControleurPartie controleurBob = controleur(bob, partie -> ecranDeBob.add(partie.position()), new FauxMoteur());

    private ControleurPartie controleur(Joueur joueur, ObservateurPartie vue, MoteurEchecs moteur) {
        return new ControleurPartie(joueur, vue, moteur, new CalculElo(), depotParties, depotJoueurs, files, IMMEDIAT,
                Clock.systemUTC());
    }

    private PartieEntreJoueurs partieEnCours() {
        PartieEntreJoueurs partie = controleurAlice.creerPartie(RAPIDE, Couleur.BLANC, null);
        controleurBob.rejoindrePartie(partie);
        return partie;
    }

    @Test
    void uneCadenceHorsDeLaListeEstRefusee_RG5() {
        Cadence inventee = new Cadence(Duration.ofMinutes(7), Duration.ZERO, CategorieCadence.BLITZ);

        assertThrows(ActionRefuseeException.class, () -> controleurAlice.creerPartie(inventee, Couleur.BLANC, null));
        assertTrue(alice.peutJouer());
    }

    @Test
    void chaqueCadenceDeLaListeAppartientAUneFamille_RG5() {
        for (CategorieCadence categorie : CategorieCadence.values()) {
            assertTrue(Cadence.DISPONIBLES.stream().anyMatch(cadence -> cadence.categorie() == categorie));
        }
    }

    @Test
    void uneCreationRendLaPartieVisibleDesAutres() {
        PartieEntreJoueurs partie = controleurAlice.creerPartie(RAPIDE, Couleur.BLANC, null);

        assertEquals(List.of(partie), controleurBob.consulterPartiesOuvertes());
    }

    @Test
    void unJoueurSuspenduNePeutPasCreerDePartie_RG8() {
        alice.suspendre();

        assertThrows(ActionRefuseeException.class, () -> controleurAlice.creerPartie(RAPIDE, Couleur.BLANC, null));
    }

    @Test
    void unJoueurEnPartieNePeutPasEnCommencerUneAutre_RG11() {
        controleurAlice.creerPartie(RAPIDE, Couleur.BLANC, null);

        assertThrows(ActionRefuseeException.class, () -> controleurAlice.creerPartie(RAPIDE, Couleur.BLANC, null));
        assertThrows(ActionRefuseeException.class, () -> controleurAlice.demarrerPartieContreOrdinateur(FORCE, Couleur.BLANC));
    }

    @Test
    void sansPartieCouranteLesActionsSontRefusees() {
        assertThrows(ActionRefuseeException.class, () -> controleurAlice.jouerCoup("e2", "e4", null));
        assertThrows(ActionRefuseeException.class, controleurAlice::abandonner);
        assertThrows(ActionRefuseeException.class, controleurAlice::annulerPartie);
    }

    @Test
    void unCoupArriveEnTexteEtPrevientLEcranDeLAdversaire() {
        partieEnCours();
        ecranDeBob.clear();

        controleurAlice.jouerCoup("e2", "e4", null);

        assertEquals(List.of("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"), ecranDeBob);
    }

    @Test
    void unNomDeCaseInvalideEstRefuseSansToucherLaPartie() {
        PartieEntreJoueurs partie = partieEnCours();

        for (String nom : new String[] {"z9", "e", "e22", "", null, "E2"}) {
            assertThrows(ActionRefuseeException.class, () -> controleurAlice.jouerCoup(nom, "e4", null));
        }
        assertTrue(partie.coups().isEmpty());
    }

    @Test
    void lesRefusDuMetierArriventJusquAuSite_RG1_RG3() {
        partieEnCours();

        assertThrows(CoupRefuseException.class, () -> controleurAlice.jouerCoup("e2", "e5", null));
        assertThrows(CoupRefuseException.class, () -> controleurBob.jouerCoup("e7", "e5", null));
    }

    @Test
    void uneNulleSePasseEntreLesDeuxControleurs_RG4() {
        PartieEntreJoueurs partie = partieEnCours();

        controleurAlice.proposerNulle();
        controleurBob.repondreNulle(true);

        assertEquals(Optional.of(MotifFin.ACCORD), partie.motifFin());
    }

    @Test
    void refuserUneNulleLaisseLaPartieContinuer_RG4() {
        PartieEntreJoueurs partie = partieEnCours();

        controleurAlice.proposerNulle();
        controleurBob.repondreNulle(false);

        assertEquals(EtatPartie.EN_COURS, partie.etat());
    }

    @Test
    void uneFinDePartieEstArchiveeAvecLesClassements_RG6_RG7() {
        PartieEntreJoueurs partie = partieEnCours();
        int enregistrementsAvant = depotParties.enregistrements;

        controleurAlice.abandonner();

        assertEquals(enregistrementsAvant + 1, depotParties.enregistrements);
        assertEquals(List.of(partie), depotParties.partiesTermineesDe(alice));
        assertEquals(List.of(alice, bob), depotJoueurs.tous());
        assertEquals(1510, bob.classement(CategorieCadence.RAPIDE).points());
    }

    @Test
    void uneRejointeRefuseeNeLaissePasLaVueAbonnee_RG13() {
        Joueur carla = new Joueur("carla", "carla@exemple.com");
        PartieEntreJoueurs partie = controleurAlice.creerPartie(RAPIDE, Couleur.BLANC, bob);
        List<String> ecranDeCarla = new ArrayList<>();
        ControleurPartie controleurCarla = controleur(carla, vue -> ecranDeCarla.add(vue.position()), new FauxMoteur());

        assertThrows(TransitionInterditeException.class, () -> controleurCarla.rejoindrePartie(partie));

        controleurBob.rejoindrePartie(partie);
        assertTrue(ecranDeCarla.isEmpty());
    }

    @Test
    void refuserUneInvitationAnnuleLaPartie_RG13() {
        PartieEntreJoueurs partie = controleurAlice.creerPartie(RAPIDE, Couleur.BLANC, bob);

        controleurBob.refuserInvitation(partie);

        assertEquals(EtatPartie.ANNULEE, partie.etat());
    }

    @Test
    void annulerEnDebutDePartieLibereLesDeuxJoueurs_RG16() {
        PartieEntreJoueurs partie = partieEnCours();

        controleurBob.annulerPartie();

        assertEquals(EtatPartie.ANNULEE, partie.etat());
        assertTrue(alice.peutJouer());
        assertTrue(bob.peutJouer());
    }

    @Test
    void onNeRevoitQueSesPropresPartiesTerminees_RG7() {
        PartieEntreJoueurs partie = partieEnCours();
        controleurAlice.jouerCoup("e2", "e4", null);
        assertThrows(ActionRefuseeException.class, () -> controleurAlice.revoirPartie(partie));

        controleurAlice.abandonner();

        assertEquals(1, controleurBob.revoirPartie(partie).size());
        Joueur carla = new Joueur("carla", "carla@exemple.com");
        ControleurPartie controleurCarla = controleur(carla, vue -> { }, new FauxMoteur());
        assertThrows(ActionRefuseeException.class, () -> controleurCarla.revoirPartie(partie));
    }

    @Test
    void lOrdinateurRepondAuCoupDuJoueur_UC7() {
        FauxMoteur moteur = new FauxMoteur("e7e5");
        ControleurPartie controleur = controleur(alice, vue -> { }, moteur);
        controleur.demarrerPartieContreOrdinateur(FORCE, Couleur.BLANC);
        assertEquals(0, moteur.demandes);

        controleur.jouerCoup("e2", "e4", null);

        assertEquals(1, moteur.demandes);
        Partie partie = depotParties.partieActiveDe(alice).orElseThrow();
        assertEquals(List.of("e4", "e5"), partie.coups().stream().map(coup -> coup.notation()).toList());
    }

    @Test
    void avecLesNoirsLOrdinateurCommence_UC7() {
        FauxMoteur moteur = new FauxMoteur("e2e4");
        ControleurPartie controleur = controleur(alice, vue -> { }, moteur);

        controleur.demarrerPartieContreOrdinateur(FORCE, Couleur.NOIR);

        Partie partie = depotParties.partieActiveDe(alice).orElseThrow();
        assertEquals(1, partie.coups().size());
        assertEquals(Couleur.NOIR, partie.trait());
    }

    @Test
    void lOrdinateurComprendLaPromotionEnNotationUci() {
        FauxMoteur moteur = new FauxMoteur("h7h5", "h5h4", "h4h3", "h3g2", "g2h1q");
        ControleurPartie controleur = controleur(alice, vue -> { }, moteur);
        controleur.demarrerPartieContreOrdinateur(FORCE, Couleur.BLANC);

        controleur.jouerCoup("a2", "a3", null);
        controleur.jouerCoup("a3", "a4", null);
        controleur.jouerCoup("a4", "a5", null);
        controleur.jouerCoup("a5", "a6", null);
        controleur.jouerCoup("b2", "b3", null);

        Partie partie = depotParties.partieActiveDe(alice).orElseThrow();
        assertEquals("gxh1=D", partie.coups().getLast().notation());
    }

    @Test
    void unCoupIllegalDuMoteurEstRefuseEtRedemande_UC7_E2() {
        FauxMoteur moteur = new FauxMoteur("e7e2", "e7e5");
        ControleurPartie controleur = controleur(alice, vue -> { }, moteur);
        controleur.demarrerPartieContreOrdinateur(FORCE, Couleur.BLANC);

        controleur.jouerCoup("e2", "e4", null);

        assertEquals(2, moteur.demandes);
        Partie partie = depotParties.partieActiveDe(alice).orElseThrow();
        assertEquals(EtatPartie.EN_COURS, partie.etat());
        assertEquals(2, partie.coups().size());
    }

    @Test
    void unMoteurMuetEstRedemandeUneFoisPuisLaPartieEstAnnulee_UC7_E1_RG15() {
        FauxMoteur moteur = new FauxMoteur();
        ControleurPartie controleur = controleur(alice, vue -> { }, moteur);
        controleur.demarrerPartieContreOrdinateur(FORCE, Couleur.BLANC);

        controleur.jouerCoup("e2", "e4", null);

        assertEquals(2, moteur.demandes);
        assertEquals(Optional.empty(), depotParties.partieActiveDe(alice));
        assertTrue(alice.peutJouer());
    }

    @Test
    void uneReponseIncomprehensibleDuMoteurCompteCommeUnEchec_UC7() {
        FauxMoteur moteur = new FauxMoteur("zzzz", "e7e5");
        ControleurPartie controleur = controleur(alice, vue -> { }, moteur);
        controleur.demarrerPartieContreOrdinateur(FORCE, Couleur.BLANC);

        controleur.jouerCoup("e2", "e4", null);

        assertEquals(2, moteur.demandes);
    }

    @Test
    void leMoteurRecoitLaPositionComplete() {
        FauxMoteur moteur = new FauxMoteur("e7e5");
        ControleurPartie controleur = controleur(alice, vue -> { }, moteur);
        controleur.demarrerPartieContreOrdinateur(FORCE, Couleur.BLANC);

        controleur.jouerCoup("e2", "e4", null);

        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", moteur.dernierePosition);
    }

    @Test
    void uneFinDePartieNeLaisseAucunCoupPossible() {
        PartieEntreJoueurs partie = partieEnCours();
        controleurAlice.abandonner();

        assertThrows(TransitionInterditeException.class, () -> controleurBob.jouerCoup("e7", "e5", null));
        assertEquals(Optional.of(Resultat.VICTOIRE_NOIRS), partie.resultat());
        assertEquals(1490, alice.classement(CategorieCadence.RAPIDE).points());
    }

    /** The engine thinks outside the queue: the player can still act, and its late move is ignored. */
    @Test
    @Timeout(LIMITE_SECONDES)
    void abandonnerPendantQueLeMoteurReflechitEstPossible_UC7() throws Exception {
        CountDownLatch moteurLance = new CountDownLatch(1);
        CountDownLatch repondre = new CountDownLatch(1);
        MoteurEchecs moteurLent = (position, force) -> {
            moteurLance.countDown();
            attendre(repondre);
            return "e7e5";
        };
        Partie partie;
        try (ExecutorService fond = Executors.newVirtualThreadPerTaskExecutor()) {
            ControleurPartie controleur = new ControleurPartie(alice, vue -> { }, moteurLent, new CalculElo(),
                    depotParties, depotJoueurs, files, fond, Clock.systemUTC());
            controleur.demarrerPartieContreOrdinateur(FORCE, Couleur.BLANC);
            partie = depotParties.partieActiveDe(alice).orElseThrow();
            controleur.jouerCoup("e2", "e4", null);
            moteurLance.await();

            try {
                controleur.abandonner();
            } finally {
                repondre.countDown();
            }
        }
        assertEquals(Optional.of(MotifFin.ABANDON), partie.motifFin());
        assertEquals(1, partie.coups().size());
    }

    private static void attendre(CountDownLatch signal) {
        try {
            signal.await();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruption);
        }
    }
}
