package echecs.metier.joueur;

public class Classement {
    public static final int POINTS_INITIAUX = 400;

    private int points;

    public Classement() {
        this(POINTS_INITIAUX);
    }

    Classement(int points) {
        this.points = points;
    }

    public int points() {
        return points;
    }

    public void modifierPoints(int variation) {
        points += variation;
    }
}
