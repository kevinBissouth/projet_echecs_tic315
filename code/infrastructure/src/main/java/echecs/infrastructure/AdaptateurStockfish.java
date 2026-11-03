package echecs.infrastructure;

import echecs.controle.MoteurEchecs;
import echecs.controle.MoteurIndisponibleException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Adapter pattern: translates {@link MoteurEchecs} into UCI, the text protocol of chess engines.
 *
 * <p>Each request starts its own process: no state can leak from one game to another, and no memory
 * is held between two moves, which matters on a small server.
 */
public class AdaptateurStockfish implements MoteurEchecs {
    // The site's scale starts at 100; Stockfish cannot play below 1320, so one scale is spread over the other.
    private static final int FORCE_MINIMUM = 100;
    private static final int FORCE_MAXIMUM = 3190;
    private static final int ELO_MINIMUM_DU_MOTEUR = 1320;
    private static final int MEMOIRE_MO = 16;
    private static final String AUCUN_COUP = "(none)";
    private static final String PREFIXE_COUP = "bestmove ";

    private final String executable;
    private final Duration tempsDeReflexion;
    private final Duration delaiDeReponse;

    public AdaptateurStockfish(String executable, Duration tempsDeReflexion, Duration delaiDeReponse) {
        this.executable = executable;
        this.tempsDeReflexion = tempsDeReflexion;
        this.delaiDeReponse = delaiDeReponse;
    }

    @Override
    public String calculerCoup(String position, int force) {
        Process processus = lancer();
        try (ExecutorService lecture = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> coup = lecture.submit(() -> dialoguer(processus, position, force));
            return attendre(coup, processus);
        }
    }

    /** The process is stopped here, not later: closing the executor waits for the read, which needs it dead. */
    private String attendre(Future<String> coup, Process processus) {
        try {
            return coup.get(delaiDeReponse.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException depassement) {
            throw new MoteurIndisponibleException("Stockfish n'a pas répondu à temps");
        } catch (ExecutionException echec) {
            throw new MoteurIndisponibleException("Stockfish a échoué : " + echec.getCause().getMessage());
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new MoteurIndisponibleException("Demande interrompue");
        } finally {
            processus.destroyForcibly();
        }
    }

    private Process lancer() {
        try {
            return new ProcessBuilder(executable).redirectErrorStream(true).start();
        } catch (IOException echec) {
            throw new MoteurIndisponibleException("Stockfish ne démarre pas : " + echec.getMessage());
        }
    }

    private String dialoguer(Process processus, String position, int force) throws IOException {
        BufferedWriter entree = new BufferedWriter(new OutputStreamWriter(processus.getOutputStream(), StandardCharsets.US_ASCII));
        BufferedReader sortie = new BufferedReader(new InputStreamReader(processus.getInputStream(), StandardCharsets.US_ASCII));
        envoyerCommande(entree, "setoption name Hash value " + MEMOIRE_MO);
        envoyerCommande(entree, "setoption name UCI_LimitStrength value true");
        envoyerCommande(entree, "setoption name UCI_Elo value " + eloDuMoteur(force));
        envoyerCommande(entree, "position fen " + position);
        envoyerCommande(entree, "go movetime " + tempsDeReflexion.toMillis());
        return lireMeilleurCoup(sortie);
    }

    private static void envoyerCommande(BufferedWriter entree, String commande) throws IOException {
        entree.write(commande);
        entree.newLine();
        entree.flush();
    }

    private static String lireMeilleurCoup(BufferedReader sortie) throws IOException {
        String ligne;
        while ((ligne = sortie.readLine()) != null) {
            if (ligne.startsWith(PREFIXE_COUP)) {
                String coup = ligne.substring(PREFIXE_COUP.length()).split(" ")[0];
                if (coup.equals(AUCUN_COUP)) {
                    throw new IOException("aucun coup possible");
                }
                return coup;
            }
        }
        throw new IOException("réponse interrompue");
    }

    private static int eloDuMoteur(int force) {
        int bornee = Math.clamp(force, FORCE_MINIMUM, FORCE_MAXIMUM);
        return ELO_MINIMUM_DU_MOTEUR
                + (bornee - FORCE_MINIMUM) * (FORCE_MAXIMUM - ELO_MINIMUM_DU_MOTEUR) / (FORCE_MAXIMUM - FORCE_MINIMUM);
    }
}
