package echecs.metier.jeu;

import java.util.Optional;

public class Case {
    private final String colonne;
    private final int rangee;
    private Piece piece;

    Case(String colonne, int rangee) {
        this.colonne = colonne;
        this.rangee = rangee;
    }

    public String colonne() {
        return colonne;
    }

    public int rangee() {
        return rangee;
    }

    public String nom() {
        return colonne + rangee;
    }

    public Optional<Piece> piece() {
        return Optional.ofNullable(piece);
    }

    public boolean estLibre() {
        return piece == null;
    }

    void placer(Piece nouvellePiece) {
        piece = nouvellePiece;
    }

    @Override
    public String toString() {
        return nom();
    }
}
