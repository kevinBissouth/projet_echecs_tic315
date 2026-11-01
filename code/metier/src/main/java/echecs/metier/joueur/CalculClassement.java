package echecs.metier.joueur;

/** Strategy pattern: the rating formula can change without touching the game (RG6). */
public interface CalculClassement {

    /**
     * @param score 1 for a win, 0.5 for a draw, 0 for a loss
     * @return the number of points to add to the player's rating, possibly negative
     */
    int variation(int points, int pointsAdversaire, double score);
}
