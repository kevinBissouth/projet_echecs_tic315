package echecs.metier.jeu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoupTest {
    private static final String POSITION = "peu importe ici";
    private final Plateau plateau = Plateau.vide();

    private Coup coup(String depart, String arrivee, Piece jouee, Piece prise, Piece promue) {
        return new Coup(1, plateau.caseEn(depart.substring(0, 1), Integer.parseInt(depart.substring(1))),
                plateau.caseEn(arrivee.substring(0, 1), Integer.parseInt(arrivee.substring(1))),
                jouee, prise, promue, POSITION);
    }

    @Test
    void laNotationSuitLExempleDuDiagrammeDObjets() {
        Pion pion = new Pion(Couleur.BLANC);

        assertEquals("e4", coup("e2", "e4", pion, null, null).notation());
        assertEquals("exd5", coup("e4", "d5", pion, new Pion(Couleur.NOIR), null).notation());
    }

    @Test
    void laNotationUtiliseLesInitialesFrancaises() {
        assertEquals("Cf3", coup("g1", "f3", new Cavalier(Couleur.BLANC), null, null).notation());
        assertEquals("Dxd8", coup("d1", "d8", new Dame(Couleur.BLANC), new Dame(Couleur.NOIR), null).notation());
    }

    @Test
    void laNotationDesRoquesEtDeLaPromotion() {
        Roi roi = new Roi(Couleur.BLANC);

        assertEquals("O-O", coup("e1", "g1", roi, null, null).notation());
        assertEquals("O-O-O", coup("e1", "c1", roi, null, null).notation());
        assertEquals("a8=D", coup("a7", "a8", new Pion(Couleur.BLANC), null, new Dame(Couleur.BLANC)).notation());
    }

    @Test
    void unePriseOuUnCoupDePionRemetLaRegleDesCinquanteCoupsAZero_RG4() {
        assertTrue(coup("e2", "e4", new Pion(Couleur.BLANC), null, null).estUnePriseOuUnCoupDePion());
        assertTrue(coup("d1", "d8", new Dame(Couleur.BLANC), new Dame(Couleur.NOIR), null).estUnePriseOuUnCoupDePion());
        assertFalse(coup("g1", "f3", new Cavalier(Couleur.BLANC), null, null).estUnePriseOuUnCoupDePion());
    }
}
