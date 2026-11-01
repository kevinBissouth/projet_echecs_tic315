package echecs.metier.partie;

/** This event is not accepted in this state of the game, or not from this player. */
public class TransitionInterditeException extends RuntimeException {
    public TransitionInterditeException(String evenement, EtatPartie etat) {
        super("Action « " + evenement + " » refusée : partie " + etat);
    }
}
