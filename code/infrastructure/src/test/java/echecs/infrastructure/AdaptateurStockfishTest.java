package echecs.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import echecs.controle.MoteurIndisponibleException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Uses small scripts that behave like an engine: the real Stockfish is checked in the Docker image. */
class AdaptateurStockfishTest {
    private static final String POSITION = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final Duration REFLEXION = Duration.ofMillis(10);
    private static final Duration DELAI = Duration.ofSeconds(5);
    private static final int FORCE = 100;
    private static final int LIMITE_SECONDES = 5;

    @TempDir
    Path dossier;

    private String script(String corps) throws IOException {
        Path fichier = dossier.resolve("moteur.sh");
        Files.writeString(fichier, "#!/bin/sh\n" + corps + "\n");
        Files.setPosixFilePermissions(fichier, PosixFilePermissions.fromString("rwx------"));
        return fichier.toString();
    }

    private AdaptateurStockfish adaptateur(String executable, Duration delai) {
        return new AdaptateurStockfish(executable, REFLEXION, delai);
    }

    @Test
    void leCoupEstLuDansLaReponseDuMoteur() throws IOException {
        String moteur = script("echo 'info depth 1'; echo 'bestmove e2e4 ponder e7e5'; cat > /dev/null");

        assertEquals("e2e4", adaptateur(moteur, DELAI).calculerCoup(POSITION, FORCE));
    }

    @Test
    void lesCommandesUciSontEnvoyees() throws IOException {
        Path journal = dossier.resolve("commandes.txt");
        String moteur = script("while read ligne; do echo \"$ligne\" >> " + journal
                + "; case \"$ligne\" in go*) echo 'bestmove a2a3';; esac; done");

        adaptateur(moteur, DELAI).calculerCoup(POSITION, FORCE);

        String commandes = Files.readString(journal);
        assertTrue(commandes.contains("setoption name UCI_Elo value 1320"));
        assertTrue(commandes.contains("position fen " + POSITION));
        assertTrue(commandes.contains("go movetime 10"));
    }

    @Test
    void uneForceHorsLimitesEstRameneeDansLesBornes() throws IOException {
        Path journal = dossier.resolve("commandes.txt");
        String moteur = script("while read ligne; do echo \"$ligne\" >> " + journal
                + "; case \"$ligne\" in go*) echo 'bestmove a2a3';; esac; done");

        adaptateur(moteur, DELAI).calculerCoup(POSITION, 9999);

        assertTrue(Files.readString(journal).contains("UCI_Elo value 3190"));
    }

    @Test
    @Timeout(LIMITE_SECONDES)
    void unMoteurMuetEstSignaleSansLAttendre_UC7_E1() throws IOException {
        String moteur = script("exec sleep 30");

        assertThrows(MoteurIndisponibleException.class,
                () -> adaptateur(moteur, Duration.ofMillis(200)).calculerCoup(POSITION, FORCE));
    }

    @Test
    void unExecutableAbsentEstSignale() {
        assertThrows(MoteurIndisponibleException.class,
                () -> adaptateur("/chemin/qui/n/existe/pas", DELAI).calculerCoup(POSITION, FORCE));
    }

    @Test
    void unMoteurQuiSArreteEstSignale() throws IOException {
        String moteur = script("echo 'info depth 1'");

        assertThrows(MoteurIndisponibleException.class, () -> adaptateur(moteur, DELAI).calculerCoup(POSITION, FORCE));
    }

    @Test
    void unePositionSansCoupPossibleEstSignalee() throws IOException {
        String moteur = script("echo 'bestmove (none)'; cat > /dev/null");

        assertThrows(MoteurIndisponibleException.class, () -> adaptateur(moteur, DELAI).calculerCoup(POSITION, FORCE));
    }
}
