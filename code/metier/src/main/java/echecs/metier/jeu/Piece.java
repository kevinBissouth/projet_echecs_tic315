package echecs.metier.jeu;

import java.util.ArrayList;
import java.util.List;

public abstract class Piece {
    private final Couleur couleur;
    private int nombreDeplacements = 0;

    protected Piece(Couleur couleur) {
        this.couleur = couleur;
    }

    public Couleur couleur() {
        return couleur;
    }

    public boolean aDejaBouge() {
        return nombreDeplacements > 0;
    }

    /**
     * The squares this piece controls, even when one of its own pieces stands there. Kept apart
     * from {@link #deplacementsPossibles}: castling asks whether a square is attacked, and that
     * question must not depend on the moves of the other king.
     */
    public abstract List<Case> casesAttaquees(Plateau plateau, Case depart);

    /** The moves allowed by this piece's own rule; the safety of the king is checked by the board. */
    public List<Case> deplacementsPossibles(Plateau plateau, Case depart) {
        return casesAttaquees(plateau, depart).stream().filter(arrivee -> !occupeeParUnAllie(arrivee)).toList();
    }

    /** The letter of this piece in FEN, the text format understood by chess engines. */
    abstract char lettreFen();

    /** The initial of this piece in French algebraic notation. */
    abstract String initiale();

    abstract Piece nouvelleInstance();

    final Piece copie() {
        Piece copie = nouvelleInstance();
        copie.nombreDeplacements = nombreDeplacements;
        return copie;
    }

    final char symboleFen() {
        return couleur == Couleur.BLANC ? lettreFen() : Character.toLowerCase(lettreFen());
    }

    void marquerDeplacee() {
        nombreDeplacements++;
    }

    protected final boolean occupeeParUnAllie(Case visee) {
        return visee.piece().filter(occupant -> occupant.couleur == couleur).isPresent();
    }

    /** Sliding pieces: each direction runs until the first occupied square, included. */
    protected final List<Case> casesEnLigne(Plateau plateau, Case depart, List<Direction> directions) {
        List<Case> cases = new ArrayList<>();
        for (Direction direction : directions) {
            var suivante = plateau.decaler(depart, direction);
            while (suivante.isPresent()) {
                cases.add(suivante.get());
                if (!suivante.get().estLibre()) {
                    break;
                }
                suivante = plateau.decaler(suivante.get(), direction);
            }
        }
        return cases;
    }

    /** Pieces that move by one jump: the king and the knight. */
    protected final List<Case> casesASaut(Plateau plateau, Case depart, List<Direction> directions) {
        return directions.stream().flatMap(direction -> plateau.decaler(depart, direction).stream()).toList();
    }
}
