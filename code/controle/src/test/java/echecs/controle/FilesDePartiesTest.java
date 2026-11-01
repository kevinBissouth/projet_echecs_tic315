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
import echecs.metier.partie.PartieEntreJoueurs;
import echecs.metier.partie.TransitionInterditeException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FilesDePartiesTest {
    private static final Cadence RAPIDE = new Cadence(Duration.ofMinutes(10), Duration.ofSeconds(5), CategorieCadence.RAPIDE);
    private static final Cadence TROP_COURTE = new Cadence(Duration.ZERO, Duration.ZERO, CategorieCadence.BULLET);
    private static final int JOUEURS_SIMULTANES = 32;
    private static final int REPETITIONS = 40;

    private final FilesDeParties files = new FilesDeParties();

    private PartieEntreJoueurs partie(Cadence cadence) {
        Joueur alice = new Joueur("alice", "alice@exemple.com");
        Joueur bob = new Joueur("bob", "bob@exemple.com");
        PartieEntreJoueurs partie = new PartieEntreJoueurs(alice, cadence, Couleur.BLANC, null, new CalculElo(), Clock.systemUTC());
        partie.rejoindre(bob);
        files.ajouter(partie);
        return partie;
    }

    @Test
    void uneActionRenvoieSonResultat() {
        PartieEntreJoueurs partie = partie(RAPIDE);

        assertEquals(EtatPartie.EN_COURS, files.executer(partie, partie::etat));
    }

    @Test
    void uneExceptionMetierArriveTelleQuelleChezLAppelant() {
        PartieEntreJoueurs partie = partie(RAPIDE);

        assertThrows(CoupRefuseException.class, () -> files.executer(partie, () -> {
            throw new CoupRefuseException("refusé");
        }));
    }

    @Test
    void uneActionTrouveLaPartieDansLOrdreDesDemandes() {
        PartieEntreJoueurs partie = partie(RAPIDE);
        List<Integer> ordre = new ArrayList<>();

        for (int numero = 0; numero < REPETITIONS; numero++) {
            int enCours = numero;
            files.executer(partie, () -> ordre.add(enCours));
        }

        assertEquals(REPETITIONS, ordre.size());
        for (int numero = 0; numero < REPETITIONS; numero++) {
            assertEquals(numero, ordre.get(numero));
        }
    }

    /** Many requests arrive at once for the same move: exactly one may be played. */
    @Test
    void deuxActionsSimultaneesNeSeMelangentJamais() throws Exception {
        for (int repetition = 0; repetition < REPETITIONS; repetition++) {
            PartieEntreJoueurs partie = partie(RAPIDE);
            Joueur blancs = partie.createur();
            AtomicInteger coupsJoues = new AtomicInteger();

            enParallele(() -> {
                try {
                    files.executer(partie, () -> partie.jouerCoup(blancs, partie.plateau().caseEn("e", 2),
                            partie.plateau().caseEn("e", 4), null));
                    coupsJoues.incrementAndGet();
                } catch (CoupRefuseException | TransitionInterditeException attendu) {
                    // Every request but the first one is refused: it is no longer white's turn.
                    assertTrue(attendu.getMessage() != null);
                }
            });

            assertEquals(1, coupsJoues.get());
            assertEquals(1, partie.coups().size());
        }
    }

    /** The classic race: both try to end the game. It ends once, and ratings change once. */
    @Test
    void uneFinDePartieSimultaneeNeChangeLesClassementsQuUneFois() throws Exception {
        for (int repetition = 0; repetition < REPETITIONS; repetition++) {
            PartieEntreJoueurs partie = partie(RAPIDE);
            Joueur blancs = partie.createur();
            Joueur noirs = partie.participations().getLast().joueur();
            AtomicInteger abandons = new AtomicInteger();

            enParallele(() -> {
                try {
                    files.executer(partie, () -> partie.abandonner(blancs));
                    abandons.incrementAndGet();
                } catch (TransitionInterditeException attendu) {
                    assertTrue(attendu.getMessage() != null);
                }
            });

            assertEquals(1, abandons.get());
            int total = blancs.classement(CategorieCadence.RAPIDE).points() + noirs.classement(CategorieCadence.RAPIDE).points();
            assertEquals(3000, total);
        }
    }

    @Test
    void uneActionEnCoursNEmpechePasUneAutrePartieDAvancer() throws Exception {
        PartieEntreJoueurs lente = partie(RAPIDE);
        PartieEntreJoueurs autre = partieDe("carla", "dan");
        CountDownLatch terminer = new CountDownLatch(1);
        CountDownLatch commencee = new CountDownLatch(1);
        try (ExecutorService fond = Executors.newSingleThreadExecutor()) {
            Future<?> attente = fond.submit(() -> files.executer(lente, () -> {
                commencee.countDown();
                try {
                    terminer.await();
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                }
            }));
            commencee.await();

            assertEquals(EtatPartie.EN_COURS, files.executer(autre, autre::etat));

            terminer.countDown();
            attente.get();
        }
    }

    @Test
    void uneActionSurUnePartieFinieSExecuteSansFile() {
        PartieEntreJoueurs partie = partie(RAPIDE);
        partie.abandonner(partie.createur());
        files.liberer(partie);

        assertEquals(EtatPartie.TERMINEE, files.executer(partie, partie::etat));
    }

    @Test
    void laVerificationPeriodiqueFaitTomberLeDrapeau_RG5() {
        PartieEntreJoueurs partie = partie(TROP_COURTE);

        files.verifierToutesLesPendules();

        assertEquals(Optional.of(MotifFin.TEMPS_ECOULE), partie.motifFin());
    }

    private PartieEntreJoueurs partieDe(String premier, String second) {
        Joueur blancs = new Joueur(premier, premier + "@exemple.com");
        PartieEntreJoueurs partie = new PartieEntreJoueurs(blancs, RAPIDE, Couleur.BLANC, null, new CalculElo(), Clock.systemUTC());
        partie.rejoindre(new Joueur(second, second + "@exemple.com"));
        files.ajouter(partie);
        return partie;
    }

    private static void enParallele(Runnable action) throws Exception {
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<?>> taches = new ArrayList<>();
        try (ExecutorService fils = Executors.newFixedThreadPool(JOUEURS_SIMULTANES)) {
            for (int fil = 0; fil < JOUEURS_SIMULTANES; fil++) {
                taches.add(fils.submit(() -> {
                    depart.await();
                    action.run();
                    return null;
                }));
            }
            depart.countDown();
            for (Future<?> tache : taches) {
                tache.get();
            }
        }
    }
}
