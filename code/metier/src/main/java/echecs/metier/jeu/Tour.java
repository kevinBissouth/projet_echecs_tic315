package echecs.metier.jeu;

import java.util.List;

public class Tour extends Piece {
    public Tour(Couleur couleur) {
        super(couleur);
    }

    @Override
    public List<Case> casesAttaquees(Plateau plateau, Case depart) {
        return casesEnLigne(plateau, depart, Direction.ORTHOGONALES);
    }

    @Override
    char lettreFen() {
        return 'R';
    }

    @Override
    String initiale() {
        return "T";
    }

    @Override
    Piece nouvelleInstance() {
        return new Tour(couleur());
    }
}
