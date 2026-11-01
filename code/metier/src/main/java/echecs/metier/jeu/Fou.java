package echecs.metier.jeu;

import java.util.List;

public class Fou extends Piece {
    public Fou(Couleur couleur) {
        super(couleur);
    }

    @Override
    public List<Case> casesAttaquees(Plateau plateau, Case depart) {
        return casesEnLigne(plateau, depart, Direction.DIAGONALES);
    }

    @Override
    char lettreFen() {
        return 'B';
    }

    @Override
    String initiale() {
        return "F";
    }

    @Override
    Piece nouvelleInstance() {
        return new Fou(couleur());
    }
}
