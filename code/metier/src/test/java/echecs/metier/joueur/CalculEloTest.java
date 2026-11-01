package echecs.metier.joueur;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CalculEloTest {
    private static final double VICTOIRE = 1;
    private static final double NULLE = 0.5;
    private static final double DEFAITE = 0;

    private final CalculElo calcul = new CalculElo();

    @Test
    void leVainqueurLegerementMieuxClasseGagneMoinsQueLaMoitieDeK() {
        assertEquals(9, calcul.variation(1500, 1480, VICTOIRE));
    }

    @Test
    void lePerdantPerdAutantQueLeVainqueurGagne() {
        assertEquals(-9, calcul.variation(1480, 1500, DEFAITE));
    }

    @Test
    void surUneNulleLeMieuxClassePerdEtLAutreGagne() {
        assertEquals(-1, calcul.variation(1500, 1480, NULLE));
        assertEquals(1, calcul.variation(1480, 1500, NULLE));
    }

    @Test
    void entreJoueursDeMemeNiveauUneVictoireRapporteLaMoitieDeK() {
        assertEquals(10, calcul.variation(1500, 1500, VICTOIRE));
        assertEquals(0, calcul.variation(1500, 1500, NULLE));
    }

    @Test
    void battreUnJoueurBienPlusFortRapportePresqueToutK() {
        assertEquals(19, calcul.variation(1200, 1800, VICTOIRE));
    }

    @Test
    void leCoefficientKEstParametrable() {
        assertEquals(16, new CalculElo(32).variation(1500, 1500, VICTOIRE));
    }

    @Test
    void unCoefficientNulOuNegatifEstRefuse() {
        assertThrows(IllegalArgumentException.class, () -> new CalculElo(0));
        assertThrows(IllegalArgumentException.class, () -> new CalculElo(-20));
    }

    @Test
    void unScoreHorsDeZeroAUnEstRefuse() {
        assertThrows(IllegalArgumentException.class, () -> calcul.variation(1500, 1500, 1.5));
        assertThrows(IllegalArgumentException.class, () -> calcul.variation(1500, 1500, -0.1));
    }
}
