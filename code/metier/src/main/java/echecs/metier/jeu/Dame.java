package echecs.metier.jeu;

import java.util.List;

public class Dame extends Piece {
    public Dame(Couleur couleur) {
        super(couleur);
    }

    @Override
    public List<Case> casesAttaquees(Plateau plateau, Case depart) {
        return casesEnLigne(plateau, depart, Direction.TOUTES);
    }

    @Override
    char lettreFen() {
        return 'Q';
    }

    @Override
    String initiale() {
        return "D";
    }

    @Override
    Piece nouvelleInstance() {
        return new Dame(couleur());
    }
}
