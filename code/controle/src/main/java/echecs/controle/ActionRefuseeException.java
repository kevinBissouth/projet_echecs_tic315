package echecs.controle;

/** The request makes no sense for this player: no current game, unknown time control, wrong game. */
public class ActionRefuseeException extends RuntimeException {
    public ActionRefuseeException(String motif) {
        super(motif);
    }
}
