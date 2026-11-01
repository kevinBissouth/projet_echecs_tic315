package echecs.metier.jeu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Perft counts every sequence of legal moves down to a depth and compares it with published
 * results (chessprogramming.org/Perft_Results). One bug in castling, en passant, promotion or
 * pins changes the total.
 */
class PerftTest {
    private static final String COLONNES = "abcdefgh";
    private static final int TAILLE = 8;

    @ParameterizedTest(name = "{0}, profondeur {3}")
    @CsvSource({
            "position initiale, rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR, BLANC, 1, 20",
            "position initiale, rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR, BLANC, 2, 400",
            "position initiale, rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR, BLANC, 3, 8902",
            "kiwipete, r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R, BLANC, 1, 48",
            "kiwipete, r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R, BLANC, 2, 2039",
            "kiwipete, r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R, BLANC, 3, 97862",
            "position 3, 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8, BLANC, 1, 14",
            "position 3, 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8, BLANC, 2, 191",
            "position 3, 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8, BLANC, 3, 2812",
            "position 3, 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8, BLANC, 4, 43238",
            "position 4, r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1, BLANC, 1, 6",
            "position 4, r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1, BLANC, 2, 264",
            "position 4, r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1, BLANC, 3, 9467",
    })
    void leNombreDeCoupsLegauxCorrespondAuxResultatsPublies(String nom, String placement, Couleur trait,
                                                            int profondeur, long attendu) {
        assertEquals(attendu, perft(Positions.depuisFen(placement), trait, profondeur));
    }

    private long perft(Plateau plateau, Couleur trait, int profondeur) {
        if (profondeur == 0) {
            return 1;
        }
        long total = 0;
        for (int colonne = 0; colonne < TAILLE; colonne++) {
            for (int rangee = 1; rangee <= TAILLE; rangee++) {
                Case depart = plateau.caseEn(COLONNES.substring(colonne, colonne + 1), rangee);
                if (depart.piece().filter(piece -> piece.couleur() == trait).isPresent()) {
                    for (Case arrivee : plateau.destinationsLegales(depart)) {
                        total += perftApresCoup(plateau, trait, profondeur, depart, arrivee);
                    }
                }
            }
        }
        return total;
    }

    private long perftApresCoup(Plateau plateau, Couleur trait, int profondeur, Case depart, Case arrivee) {
        boolean promotion = depart.piece().orElseThrow() instanceof Pion pion && pion.atteintLaDerniereRangee(arrivee);
        List<TypePromotion> choix = promotion ? List.of(TypePromotion.values()) : java.util.Collections.singletonList(null);
        long total = 0;
        for (TypePromotion typePromotion : choix) {
            Plateau suite = plateau.copie();
            suite.deplacer(suite.caseEn(depart.colonne(), depart.rangee()),
                    suite.caseEn(arrivee.colonne(), arrivee.rangee()), typePromotion);
            total += perft(suite, trait.adverse(), profondeur - 1);
        }
        return total;
    }
}
