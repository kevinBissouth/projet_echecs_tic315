package echecs.metier.partie;

/** Observer pattern: the game warns its views without knowing them. They re-read the game they receive. */
public interface ObservateurPartie {
    void actualiser(Partie partie);
}
