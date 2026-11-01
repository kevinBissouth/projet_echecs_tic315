package echecs.metier.jeu;

import java.util.Map;
import java.util.function.Function;

/** Builds a board from the placement field of a FEN text. Pieces count as not having moved yet. */
final class Positions {
    private static final Map<Character, Function<Couleur, Piece>> PIECES = Map.of(
            'K', Roi::new, 'Q', Dame::new, 'R', Tour::new, 'B', Fou::new, 'N', Cavalier::new, 'P', Pion::new);
    private static final String COLONNES = "abcdefgh";
    private static final int DERNIERE_RANGEE = 8;

    private Positions() {
    }

    static Plateau depuisFen(String placement) {
        Plateau plateau = Plateau.vide();
        int rangee = DERNIERE_RANGEE;
        for (String ligne : placement.split("/")) {
            int colonne = 0;
            for (char symbole : ligne.toCharArray()) {
                if (Character.isDigit(symbole)) {
                    colonne += Character.getNumericValue(symbole);
                } else {
                    Couleur couleur = Character.isUpperCase(symbole) ? Couleur.BLANC : Couleur.NOIR;
                    Piece piece = PIECES.get(Character.toUpperCase(symbole)).apply(couleur);
                    plateau.caseEn(COLONNES.substring(colonne, colonne + 1), rangee).placer(piece);
                    colonne++;
                }
            }
            rangee--;
        }
        return plateau;
    }
}
