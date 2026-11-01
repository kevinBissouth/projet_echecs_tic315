package echecs.controle;

import echecs.metier.partie.Partie;
import echecs.metier.partie.PartieEntreJoueurs;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * One queue of actions per game: the server handles players in parallel, but a game handles one
 * action at a time, in order. Other games are not slowed down.
 *
 * <p>Never call {@code executer} from inside an action, or from an observer: the action would wait
 * for itself. Never put a slow call in an action either: the engine thinks outside the queue.
 */
public class FilesDeParties {
    private final Map<Partie, ExecutorService> files = new ConcurrentHashMap<>();

    void ajouter(Partie partie) {
        files.put(partie, Executors.newSingleThreadExecutor(Thread.ofVirtual().factory()));
    }

    /** The game is over: its queue is no longer needed. Tasks already queued still run. */
    void liberer(Partie partie) {
        ExecutorService file = files.remove(partie);
        if (file != null) {
            file.shutdown();
        }
    }

    /** Runs the action in the game's queue, waits for it, and throws what it threw. */
    public <T> T executer(Partie partie, Supplier<T> action) {
        ExecutorService file = files.get(partie);
        if (file == null) {
            // A finished game no longer changes: it only refuses or answers, so no queue is needed.
            return action.get();
        }
        try {
            return file.submit(action::get).get();
        } catch (ExecutionException echec) {
            if (echec.getCause() instanceof RuntimeException erreur) {
                throw erreur;
            }
            throw new IllegalStateException(echec.getCause());
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Action interrompue", interruption);
        }
    }

    public void executer(Partie partie, Runnable action) {
        executer(partie, () -> {
            action.run();
            return null;
        });
    }

    /** Called regularly: a flag must fall even if nobody plays anymore (RG5). */
    public void verifierToutesLesPendules() {
        List.copyOf(files.keySet()).forEach(partie -> {
            if (partie instanceof PartieEntreJoueurs entreJoueurs) {
                executer(partie, entreJoueurs::verifierTemps);
            }
        });
    }
}
