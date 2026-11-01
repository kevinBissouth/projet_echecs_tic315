package echecs.metier.joueur;

/** The FIDE Elo formula. */
public class CalculElo implements CalculClassement {
    private static final int K_PAR_DEFAUT = 20;
    private static final double ECART_DE_REFERENCE = 400;
    private static final double BASE = 10;
    private static final double SCORE_MINIMUM = 0;
    private static final double SCORE_MAXIMUM = 1;

    private final int k;

    public CalculElo() {
        this(K_PAR_DEFAUT);
    }

    public CalculElo(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Le coefficient K doit être positif : " + k);
        }
        this.k = k;
    }

    @Override
    public int variation(int points, int pointsAdversaire, double score) {
        if (score < SCORE_MINIMUM || score > SCORE_MAXIMUM) {
            throw new IllegalArgumentException("Le score doit être compris entre 0 et 1 : " + score);
        }
        return (int) Math.round(k * (score - scoreAttendu(points, pointsAdversaire)));
    }

    private double scoreAttendu(int points, int pointsAdversaire) {
        return 1 / (1 + Math.pow(BASE, (pointsAdversaire - points) / ECART_DE_REFERENCE));
    }
}
