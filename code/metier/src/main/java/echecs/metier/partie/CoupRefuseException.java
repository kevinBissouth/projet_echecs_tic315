package echecs.metier.partie;

/** The game accepts moves, but not this one: not the player's turn, or an illegal move. */
public class CoupRefuseException extends RuntimeException {
    public CoupRefuseException(String motif) {
        super(motif);
    }
}
