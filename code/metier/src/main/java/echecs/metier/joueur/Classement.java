package echecs.metier.joueur;

public class Classement {
    public static final int POINTS_INITIAUX = 1500;

    private int points = POINTS_INITIAUX;

    public int points() {
        return points;
    }

    public void modifierPoints(int variation) {
        points += variation;
    }
}
