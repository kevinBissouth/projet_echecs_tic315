package echecs.metier.jeu;

import java.util.List;

public class Cavalier extends Piece {
    private static final List<Direction> SAUTS = List.of(
            new Direction(1, 2), new Direction(2, 1), new Direction(2, -1), new Direction(1, -2),
            new Direction(-1, -2), new Direction(-2, -1), new Direction(-2, 1), new Direction(-1, 2));

    public Cavalier(Couleur couleur) {
        super(couleur);
    }

    @Override
    public List<Case> casesAttaquees(Plateau plateau, Case depart) {
        return casesASaut(plateau, depart, SAUTS);
    }

    @Override
    char lettreFen() {
        return 'N';
    }

    @Override
    String initiale() {
        return "C";
    }

    @Override
    Piece nouvelleInstance() {
        return new Cavalier(couleur());
    }
}
