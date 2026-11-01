package echecs.metier.jeu;

import java.util.ArrayList;
import java.util.List;

public class Pion extends Piece {
    private static final int RANGEE_INITIALE_BLANCS = 2;
    private static final int RANGEE_INITIALE_NOIRS = 7;
    private static final int DERNIERE_RANGEE_BLANCS = 8;
    private static final int DERNIERE_RANGEE_NOIRS = 1;

    public Pion(Couleur couleur) {
        super(couleur);
    }

    /** A pawn attacks diagonally, unlike all other pieces, and does not capture where it advances. */
    @Override
    public List<Case> casesAttaquees(Plateau plateau, Case depart) {
        int sens = sens();
        return casesASaut(plateau, depart, List.of(new Direction(1, sens), new Direction(-1, sens)));
    }

    @Override
    public List<Case> deplacementsPossibles(Plateau plateau, Case depart) {
        List<Case> deplacements = new ArrayList<>(avances(plateau, depart));
        for (Case attaquee : casesAttaquees(plateau, depart)) {
            boolean priseOrdinaire = !attaquee.estLibre() && !occupeeParUnAllie(attaquee);
            boolean priseEnPassant = plateau.casePriseEnPassant().filter(attaquee::equals).isPresent();
            if (priseOrdinaire || priseEnPassant) {
                deplacements.add(attaquee);
            }
        }
        return deplacements;
    }

    private List<Case> avances(Plateau plateau, Case depart) {
        List<Case> avances = new ArrayList<>();
        Direction enAvant = new Direction(0, sens());
        var uneCase = plateau.decaler(depart, enAvant).filter(Case::estLibre);
        if (uneCase.isPresent()) {
            avances.add(uneCase.get());
            if (depart.rangee() == rangeeInitiale()) {
                plateau.decaler(uneCase.get(), enAvant).filter(Case::estLibre).ifPresent(avances::add);
            }
        }
        return avances;
    }

    boolean atteintLaDerniereRangee(Case arrivee) {
        return arrivee.rangee() == (couleur() == Couleur.BLANC ? DERNIERE_RANGEE_BLANCS : DERNIERE_RANGEE_NOIRS);
    }

    private int sens() {
        return couleur() == Couleur.BLANC ? 1 : -1;
    }

    private int rangeeInitiale() {
        return couleur() == Couleur.BLANC ? RANGEE_INITIALE_BLANCS : RANGEE_INITIALE_NOIRS;
    }

    @Override
    char lettreFen() {
        return 'P';
    }

    @Override
    String initiale() {
        return "";
    }

    @Override
    Piece nouvelleInstance() {
        return new Pion(couleur());
    }
}
