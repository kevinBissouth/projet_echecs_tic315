package echecs.metier.jeu;

import java.util.Optional;

/** A played move. Immutable: the history of a game is never rewritten (RG7). */
public class Coup {
    private static final String PETIT_ROQUE = "O-O";
    private static final String GRAND_ROQUE = "O-O-O";
    private static final String COLONNE_ARRIVEE_PETIT_ROQUE = "g";
    private static final int ECART_COLONNES_ROQUE = 2;

    private final int numero;
    private final Case depart;
    private final Case arrivee;
    private final Piece pieceJouee;
    private final Piece piecePrise;
    private final Piece piecePromue;
    private final String positionApres;

    public Coup(int numero, Case depart, Case arrivee, Piece pieceJouee, Piece piecePrise, Piece piecePromue,
                String positionApres) {
        this.numero = numero;
        this.depart = depart;
        this.arrivee = arrivee;
        this.pieceJouee = pieceJouee;
        this.piecePrise = piecePrise;
        this.piecePromue = piecePromue;
        this.positionApres = positionApres;
    }

    public int numero() {
        return numero;
    }

    public Case depart() {
        return depart;
    }

    public Case arrivee() {
        return arrivee;
    }

    public Piece pieceJouee() {
        return pieceJouee;
    }

    public Optional<Piece> piecePrise() {
        return Optional.ofNullable(piecePrise);
    }

    public Optional<Piece> piecePromue() {
        return Optional.ofNullable(piecePromue);
    }

    public String positionApres() {
        return positionApres;
    }

    /** The 50-move rule restarts after such a move (RG4). */
    public boolean estUnePriseOuUnCoupDePion() {
        return piecePrise != null || pieceJouee instanceof Pion;
    }

    /** Algebraic notation with French initials, without check marks or disambiguation. */
    public String notation() {
        if (estUnRoque()) {
            return arrivee.colonne().equals(COLONNE_ARRIVEE_PETIT_ROQUE) ? PETIT_ROQUE : GRAND_ROQUE;
        }
        return prefixe() + (piecePrise != null ? "x" : "") + arrivee.nom() + suffixePromotion();
    }

    private boolean estUnRoque() {
        int ecart = Math.abs(depart.colonne().charAt(0) - arrivee.colonne().charAt(0));
        return pieceJouee instanceof Roi && ecart == ECART_COLONNES_ROQUE;
    }

    private String prefixe() {
        return pieceJouee instanceof Pion && piecePrise != null ? depart.colonne() : pieceJouee.initiale();
    }

    private String suffixePromotion() {
        return piecePromue != null ? "=" + piecePromue.initiale() : "";
    }
}
