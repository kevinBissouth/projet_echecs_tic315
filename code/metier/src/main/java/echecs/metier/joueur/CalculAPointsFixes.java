package echecs.metier.joueur;

/**
 * The site's rule (RG6): a win gives 8 points, a loss takes 8, a draw changes nothing,
 * and a rating never goes below zero.
 */
public class CalculAPointsFixes implements CalculClassement {
    public static final int POINTS_PAR_PARTIE = 8;
    private static final double VICTOIRE = 1;
    private static final double DEFAITE = 0;

    @Override
    public int variation(int points, int pointsAdversaire, double score) {
        if (score == VICTOIRE) {
            return POINTS_PAR_PARTIE;
        }
        if (score == DEFAITE) {
            return -Math.min(POINTS_PAR_PARTIE, Math.max(points, 0));
        }
        return 0;
    }
}
