package echecs.metier.joueur;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CalculAPointsFixesTest {
    private static final double VICTOIRE = 1;
    private static final double NULLE = 0.5;
    private static final double DEFAITE = 0;

    private final CalculClassement calcul = new CalculAPointsFixes();

    @Test
    void uneVictoireRapporteHuitPoints_RG6() {
        assertEquals(8, calcul.variation(400, 900, VICTOIRE));
    }

    @Test
    void uneDefaiteRetireHuitPoints_RG6() {
        assertEquals(-8, calcul.variation(400, 100, DEFAITE));
    }

    @Test
    void uneNulleNeChangeRien_RG6() {
        assertEquals(0, calcul.variation(400, 400, NULLE));
    }

    @Test
    void leClassementNeDescendJamaisSousZero_RG6() {
        assertEquals(-5, calcul.variation(5, 400, DEFAITE));
        assertEquals(0, calcul.variation(0, 400, DEFAITE));
    }
}
