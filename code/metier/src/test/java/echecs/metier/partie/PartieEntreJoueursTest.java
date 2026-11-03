package echecs.metier.partie;

import static echecs.metier.partie.Jeu.caseDe;
import static echecs.metier.partie.Jeu.jouer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import echecs.metier.jeu.Coup;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.MotifFin;
import echecs.metier.joueur.CalculAPointsFixes;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.Joueur;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PartieEntreJoueursTest {
    private static final Duration DIX_MINUTES = Duration.ofMinutes(10);
    private static final Duration CINQ_SECONDES = Duration.ofSeconds(5);
    private static final Cadence RAPIDE = new Cadence(DIX_MINUTES, CINQ_SECONDES, CategorieCadence.RAPIDE);

    private static final int COUPS_DE_PION = 4;
    private static final int DEMI_COUPS_REGLE_DES_CINQUANTE = 100;
    private static final List<String[]> TRAJET_TOURS_BLANCHES = List.of(
            new String[] {"a1", "a2"}, new String[] {"a2", "a3"}, new String[] {"h1", "h2"}, new String[] {"a3", "a2"},
            new String[] {"a2", "a1"}, new String[] {"h2", "h3"}, new String[] {"a1", "a2"}, new String[] {"a2", "a3"},
            new String[] {"a3", "a2"}, new String[] {"a2", "a1"}, new String[] {"h3", "h2"}, new String[] {"a1", "a2"},
            new String[] {"a2", "a3"}, new String[] {"h2", "h1"}, new String[] {"a3", "a2"}, new String[] {"a2", "a1"});
    private static final List<String[]> TRAJET_TOUR_NOIRE = List.of(
            new String[] {"a8", "a7"}, new String[] {"a7", "a6"}, new String[] {"a6", "a8"});

    private final HorlogeDeTest horloge = new HorlogeDeTest();
    private final Joueur alice = new Joueur("alice", "alice@exemple.com");
    private final Joueur bob = new Joueur("bob", "bob@exemple.com");
    private final Joueur carla = new Joueur("carla", "carla@exemple.com");

    private PartieEntreJoueurs partieOuverte() {
        return new PartieEntreJoueurs(alice, RAPIDE, Couleur.BLANC, null, new CalculAPointsFixes(), horloge);
    }

    private PartieEntreJoueurs partieEnCours() {
        PartieEntreJoueurs partie = partieOuverte();
        partie.rejoindre(bob);
        return partie;
    }

    private static void matDuBerger(PartieEntreJoueurs partie, Joueur blancs, Joueur noirs) {
        jouer(partie, blancs, "e2", "e4");
        jouer(partie, noirs, "e7", "e5");
        jouer(partie, blancs, "d1", "h5");
        jouer(partie, noirs, "b8", "c6");
        jouer(partie, blancs, "f1", "c4");
        jouer(partie, noirs, "g8", "f6");
        jouer(partie, blancs, "h5", "f7");
    }

    @Test
    void uneNouvellePartieAttendUnAdversaireEtOccupeLeCreateur_RG11() {
        PartieEntreJoueurs partie = partieOuverte();

        assertEquals(EtatPartie.EN_ATTENTE, partie.etat());
        assertFalse(alice.peutJouer());
    }

    @Test
    void unJoueurDejaEnPartieNePeutPasEnCreerUne_RG11() {
        partieOuverte();

        assertThrows(IllegalStateException.class, this::partieOuverte);
    }

    @Test
    void rejoindreDemarreLaPartieEtDonneLaCouleurOpposee() {
        PartieEntreJoueurs partie = partieEnCours();

        assertEquals(EtatPartie.EN_COURS, partie.etat());
        assertTrue(partie.dateDebut().isPresent());
        assertEquals(Optional.of(Couleur.NOIR), partie.couleurDe(bob));
        assertFalse(bob.peutJouer());
    }

    @Test
    void uneCouleurAuHasardDonneDeuxCouleursDifferentes() {
        PartieEntreJoueurs partie = new PartieEntreJoueurs(alice, RAPIDE, null, null, new CalculAPointsFixes(), horloge);

        partie.rejoindre(bob);

        assertTrue(partie.couleurDe(alice).isPresent());
        assertNotEquals(partie.couleurDe(alice), partie.couleurDe(bob));
    }

    @Test
    void onNePeutPasRejoindreSaProprePartie_RG3() {
        PartieEntreJoueurs partie = partieOuverte();

        assertThrows(TransitionInterditeException.class, () -> partie.rejoindre(alice));
    }

    @Test
    void unJoueurSuspenduNePeutPasRejoindre_RG8() {
        PartieEntreJoueurs partie = partieOuverte();
        bob.suspendre();

        assertThrows(TransitionInterditeException.class, () -> partie.rejoindre(bob));
    }

    @Test
    void seulLInvitePeutRejoindreUnePartieSurInvitation_RG13() {
        PartieEntreJoueurs partie = new PartieEntreJoueurs(alice, RAPIDE, Couleur.BLANC, bob, new CalculAPointsFixes(), horloge);

        assertThrows(TransitionInterditeException.class, () -> partie.rejoindre(carla));
        partie.rejoindre(bob);
        assertEquals(EtatPartie.EN_COURS, partie.etat());
    }

    @Test
    void onNePeutPasSInviterSoiMeme() {
        assertThrows(IllegalArgumentException.class,
                () -> new PartieEntreJoueurs(alice, RAPIDE, Couleur.BLANC, alice, new CalculAPointsFixes(), horloge));
    }

    @Test
    void refuserLInvitationAnnuleLaPartieEtLibereLeCreateur_RG13() {
        PartieEntreJoueurs partie = new PartieEntreJoueurs(alice, RAPIDE, Couleur.BLANC, bob, new CalculAPointsFixes(), horloge);

        partie.refuserInvitation(bob);

        assertEquals(EtatPartie.ANNULEE, partie.etat());
        assertTrue(alice.peutJouer());
    }

    @Test
    void seulLInvitePeutRefuserLInvitation_RG13() {
        PartieEntreJoueurs partie = new PartieEntreJoueurs(alice, RAPIDE, Couleur.BLANC, bob, new CalculAPointsFixes(), horloge);

        assertThrows(TransitionInterditeException.class, () -> partie.refuserInvitation(carla));
    }

    @Test
    void unJoueurRechargeResteLeMemeParticipant() {
        PartieEntreJoueurs partie = partieEnCours();
        Joueur aliceRechargee = new Joueur("alice", "alice@exemple.com");

        jouer(partie, aliceRechargee, "e2", "e4");

        assertEquals(1, partie.coups().size());
    }

    @Test
    void lesBlancsCommencentPuisLeTraitAlterne_RG3() {
        PartieEntreJoueurs partie = partieEnCours();
        assertEquals(Couleur.BLANC, partie.trait());

        jouer(partie, alice, "e2", "e4");

        assertEquals(Couleur.NOIR, partie.trait());
    }

    @Test
    void jouerHorsDeSonTourEstRefuse_RG3() {
        PartieEntreJoueurs partie = partieEnCours();

        assertThrows(CoupRefuseException.class, () -> jouer(partie, bob, "e7", "e5"));
        assertTrue(partie.coups().isEmpty());
    }

    @Test
    void unNonParticipantNePeutPasJouer_RG3() {
        PartieEntreJoueurs partie = partieEnCours();

        assertThrows(CoupRefuseException.class, () -> jouer(partie, carla, "e2", "e4"));
    }

    @Test
    void jouerUnePieceAdverseEstRefuse_RG3() {
        PartieEntreJoueurs partie = partieEnCours();

        assertThrows(CoupRefuseException.class, () -> jouer(partie, alice, "e7", "e5"));
    }

    @Test
    void unCoupIllegalEstRefuseEtNeChangeRien_RG1() {
        PartieEntreJoueurs partie = partieEnCours();
        String avant = partie.position();

        assertThrows(CoupRefuseException.class, () -> jouer(partie, alice, "e2", "e5"));

        assertEquals(avant, partie.position());
        assertEquals(Couleur.BLANC, partie.trait());
    }

    @Test
    void lesCoupsSontEnregistresDansLOrdre_RG7() {
        PartieEntreJoueurs partie = partieEnCours();

        Coup premier = jouer(partie, alice, "e2", "e4");
        jouer(partie, bob, "d7", "d5");
        jouer(partie, alice, "e4", "d5");

        assertEquals(List.of(1, 2, 3), partie.coups().stream().map(Coup::numero).toList());
        assertEquals(List.of("e4", "d5", "exd5"), partie.coups().stream().map(Coup::notation).toList());
        assertEquals(premier, partie.coups().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> partie.coups().clear());
    }

    @Test
    void lePendulePerdLeTempsDuTourEtGagneLIncrement_RG5() {
        PartieEntreJoueurs partie = partieEnCours();

        horloge.avancer(Duration.ofSeconds(30));
        jouer(partie, alice, "e2", "e4");

        Duration attendu = DIX_MINUTES.minusSeconds(30).plus(CINQ_SECONDES);
        assertEquals(Optional.of(attendu), partie.participations().getFirst().tempsRestant());
        assertEquals(Optional.of(DIX_MINUTES), partie.participations().getLast().tempsRestant());
    }

    @Test
    void lePenduleNeDecompteQuePendantSonTour_RG5() {
        PartieEntreJoueurs partie = partieEnCours();
        jouer(partie, alice, "e2", "e4");

        horloge.avancer(Duration.ofSeconds(40));
        jouer(partie, bob, "e7", "e5");

        Duration attenduBob = DIX_MINUTES.minusSeconds(40).plus(CINQ_SECONDES);
        assertEquals(Optional.of(attenduBob), partie.participations().getLast().tempsRestant());
    }

    @Test
    void leTempsEcouleDonneLaDefaite_RG5() {
        PartieEntreJoueurs partie = partieEnCours();

        horloge.avancer(DIX_MINUTES);
        partie.verifierTemps();

        assertEquals(EtatPartie.TERMINEE, partie.etat());
        assertEquals(Optional.of(Resultat.VICTOIRE_NOIRS), partie.resultat());
        assertEquals(Optional.of(MotifFin.TEMPS_ECOULE), partie.motifFin());
    }

    @Test
    void unCoupJoueApresLaChuteDuDrapeauNeComptePas_RG5() {
        PartieEntreJoueurs partie = partieEnCours();
        horloge.avancer(DIX_MINUTES.plusSeconds(1));

        assertThrows(TransitionInterditeException.class, () -> jouer(partie, alice, "e2", "e4"));
        assertEquals(EtatPartie.TERMINEE, partie.etat());
        assertTrue(partie.coups().isEmpty());
    }

    @Test
    void verifierLeTempsNeFaitRienSiIlEnReste_RG5() {
        PartieEntreJoueurs partie = partieEnCours();
        horloge.avancer(Duration.ofMinutes(9));

        partie.verifierTemps();

        assertEquals(EtatPartie.EN_COURS, partie.etat());
    }

    @Test
    void leMatTermineLaPartieParLaVictoireDuJoueurQuiVientDeJouer_RG4() {
        PartieEntreJoueurs partie = partieEnCours();

        matDuBerger(partie, alice, bob);

        assertEquals(EtatPartie.TERMINEE, partie.etat());
        assertEquals(Optional.of(Resultat.VICTOIRE_BLANCS), partie.resultat());
        assertEquals(Optional.of(MotifFin.MAT), partie.motifFin());
        assertTrue(partie.dateFin().isPresent());
    }

    @Test
    void uneFinDePartieLibereLesJoueursEtRefuseLesCoups_RG11() {
        PartieEntreJoueurs partie = partieEnCours();

        matDuBerger(partie, alice, bob);

        assertTrue(alice.peutJouer());
        assertTrue(bob.peutJouer());
        assertThrows(TransitionInterditeException.class, () -> jouer(partie, bob, "a7", "a6"));
    }

    @Test
    void seulLeClassementDeLaFamilleDeLaCadenceChange_RG6() {
        PartieEntreJoueurs partie = partieEnCours();

        matDuBerger(partie, alice, bob);

        assertEquals(408, alice.classement(CategorieCadence.RAPIDE).points());
        assertEquals(392, bob.classement(CategorieCadence.RAPIDE).points());
        assertEquals(Classement.POINTS_INITIAUX, alice.classement(CategorieCadence.BLITZ).points());
        assertEquals(Classement.POINTS_INITIAUX, bob.classement(CategorieCadence.BULLET).points());
    }

    @Test
    void lAbandonEstUneDefaite_RG4() {
        PartieEntreJoueurs partie = partieEnCours();

        partie.abandonner(alice);

        assertEquals(Optional.of(Resultat.VICTOIRE_NOIRS), partie.resultat());
        assertEquals(Optional.of(MotifFin.ABANDON), partie.motifFin());
        assertEquals(392, alice.classement(CategorieCadence.RAPIDE).points());
    }

    @Test
    void unNonParticipantNePeutPasAbandonner() {
        PartieEntreJoueurs partie = partieEnCours();

        assertThrows(TransitionInterditeException.class, () -> partie.abandonner(carla));
    }

    @Test
    void uneNulleAccepteeTermineLaPartie_RG4() {
        PartieEntreJoueurs partie = partieEnCours();

        partie.proposerNulle(alice);
        partie.accepterNulle(bob);

        assertEquals(Optional.of(Resultat.NULLE), partie.resultat());
        assertEquals(Optional.of(MotifFin.ACCORD), partie.motifFin());
    }

    @Test
    void onNeRepondPasASaPropreProposition_RG4() {
        PartieEntreJoueurs partie = partieEnCours();
        partie.proposerNulle(alice);

        assertThrows(TransitionInterditeException.class, () -> partie.accepterNulle(alice));
        assertThrows(TransitionInterditeException.class, () -> partie.refuserNulle(alice));
    }

    @Test
    void onNePeutPasAccepterUneNulleQuePersonneNAProposee_RG4() {
        PartieEntreJoueurs partie = partieEnCours();

        assertThrows(TransitionInterditeException.class, () -> partie.accepterNulle(bob));
    }

    @Test
    void uneNulleRefuseeLaisseLaPartieContinuer_RG4() {
        PartieEntreJoueurs partie = partieEnCours();
        partie.proposerNulle(alice);

        partie.refuserNulle(bob);

        assertEquals(EtatPartie.EN_COURS, partie.etat());
        assertEquals(Optional.empty(), partie.nulleProposeePar());
    }

    @Test
    void jouerSansRepondreVautRefus_RG4() {
        PartieEntreJoueurs partie = partieEnCours();
        jouer(partie, alice, "e2", "e4");
        partie.proposerNulle(alice);

        jouer(partie, bob, "e7", "e5");

        assertEquals(Optional.empty(), partie.nulleProposeePar());
    }

    @Test
    void unePropositionResteValableQuandSonAuteurJoue_RG4() {
        PartieEntreJoueurs partie = partieEnCours();
        partie.proposerNulle(alice);

        jouer(partie, alice, "e2", "e4");

        assertEquals(Optional.of(Couleur.BLANC), partie.nulleProposeePar());
    }

    @Test
    void laTripleRepetitionEstUneNulleAutomatique_RG9() {
        PartieEntreJoueurs partie = partieEnCours();
        for (int aller = 0; aller < 2; aller++) {
            jouer(partie, alice, "g1", "f3");
            jouer(partie, bob, "g8", "f6");
            jouer(partie, alice, "f3", "g1");
            jouer(partie, bob, "f6", "g8");
        }

        assertEquals(Optional.of(MotifFin.TRIPLE_REPETITION), partie.motifFin());
        assertEquals(Optional.of(Resultat.NULLE), partie.resultat());
    }

    @Test
    void laRegleDesCinquanteCoupsEstUneNulleAutomatique_RG9() {
        PartieEntreJoueurs partie = partieEnCours();
        jouer(partie, alice, "a2", "a4");
        jouer(partie, bob, "a7", "a5");
        jouer(partie, alice, "h2", "h4");
        jouer(partie, bob, "h7", "h5");

        // The two rooks' routes have different lengths, so no position comes back three times.
        for (int coup = 0; partie.etat() == EtatPartie.EN_COURS; coup++) {
            String[] blanc = TRAJET_TOURS_BLANCHES.get(coup % TRAJET_TOURS_BLANCHES.size());
            jouer(partie, alice, blanc[0], blanc[1]);
            if (partie.etat() == EtatPartie.EN_COURS) {
                String[] noir = TRAJET_TOUR_NOIRE.get(coup % TRAJET_TOUR_NOIRE.size());
                jouer(partie, bob, noir[0], noir[1]);
            }
        }

        assertEquals(Optional.of(MotifFin.CINQUANTE_COUPS), partie.motifFin());
        assertEquals(Optional.of(Resultat.NULLE), partie.resultat());
        assertEquals(COUPS_DE_PION + DEMI_COUPS_REGLE_DES_CINQUANTE, partie.coups().size());
    }

    @Test
    void laPositionCompleteCompteLesCoupsPourLeMoteur() {
        PartieEntreJoueurs partie = partieEnCours();
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", partie.position());

        jouer(partie, alice, "g1", "f3");
        assertEquals("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1", partie.position());

        jouer(partie, bob, "e7", "e5");
        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/8/5N2/PPPPPPPP/RNBQKB1R w KQkq e6 0 2", partie.position());
    }

    @Test
    void lesObservateursSontPrevenusDeChaqueChangement() {
        PartieEntreJoueurs partie = partieOuverte();
        List<EtatPartie> etatsVus = new ArrayList<>();
        partie.abonner(vue -> etatsVus.add(vue.etat()));

        partie.rejoindre(bob);
        jouer(partie, alice, "e2", "e4");
        partie.abandonner(bob);

        assertEquals(List.of(EtatPartie.EN_COURS, EtatPartie.EN_COURS, EtatPartie.TERMINEE), etatsVus);
    }

    @Test
    void unCoupRefuseNePrevientPersonne() {
        PartieEntreJoueurs partie = partieEnCours();
        List<EtatPartie> etatsVus = new ArrayList<>();
        partie.abonner(vue -> etatsVus.add(vue.etat()));

        assertThrows(CoupRefuseException.class, () -> jouer(partie, bob, "e7", "e5"));

        assertTrue(etatsVus.isEmpty());
    }

    @Test
    void unObservateurDesabonneNEstPlusPrevenu() {
        PartieEntreJoueurs partie = partieEnCours();
        List<EtatPartie> etatsVus = new ArrayList<>();
        ObservateurPartie observateur = vue -> etatsVus.add(vue.etat());
        partie.abonner(observateur);
        partie.desabonner(observateur);

        jouer(partie, alice, "e2", "e4");

        assertTrue(etatsVus.isEmpty());
    }

    @Test
    void uneCaseDuPlateauSeTrouveParSonNom() {
        assertEquals("e4", caseDe(partieOuverte().plateau(), "e4").nom());
    }
}
