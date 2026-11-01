package echecs.metier.jeu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlateauTest {
    private static Case c(Plateau plateau, String nom) {
        return plateau.caseEn(nom.substring(0, 1), Integer.parseInt(nom.substring(1)));
    }

    private static void jouer(Plateau plateau, String depart, String arrivee) {
        plateau.deplacer(c(plateau, depart), c(plateau, arrivee), null);
    }

    @Test
    void laPositionInitialeEstCelleDuJeu() {
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -", new Plateau().position(Couleur.BLANC));
    }

    @Test
    void uneCaseInexistanteEstRefusee() {
        Plateau plateau = new Plateau();
        assertThrows(IllegalArgumentException.class, () -> plateau.caseEn("i", 1));
        assertThrows(IllegalArgumentException.class, () -> plateau.caseEn("a", 9));
    }

    @Test
    void unCoupIllegalEstRefuseEtNeChangeRien_RG1() {
        Plateau plateau = new Plateau();
        String avant = plateau.position(Couleur.BLANC);

        assertThrows(IllegalArgumentException.class, () -> jouer(plateau, "e2", "e5"));
        assertEquals(avant, plateau.position(Couleur.BLANC));
    }

    @Test
    void unePieceCloueeNePeutPasExposerSonRoi_RG1() {
        Plateau plateau = Positions.depuisFen("4r2k/8/8/8/8/8/4N3/4K3");

        assertFalse(plateau.estLegal(c(plateau, "e2"), c(plateau, "c3")));
    }

    @Test
    void enEchecIlFautParerLEchec_RG1() {
        Plateau plateau = Positions.depuisFen("4r2k/8/8/8/8/8/P7/4K3");

        assertTrue(plateau.enEchec(Couleur.BLANC));
        assertFalse(plateau.estLegal(c(plateau, "a2"), c(plateau, "a3")));
        assertTrue(plateau.estLegal(c(plateau, "e1"), c(plateau, "d1")));
    }

    @Test
    void lePetitRoqueDeplaceAussiLaTour_RG2() {
        Plateau plateau = Positions.depuisFen("4k3/8/8/8/8/8/8/4K2R");

        jouer(plateau, "e1", "g1");

        assertInstanceOf(Roi.class, c(plateau, "g1").piece().orElseThrow());
        assertInstanceOf(Tour.class, c(plateau, "f1").piece().orElseThrow());
        assertTrue(c(plateau, "h1").estLibre());
    }

    @Test
    void leGrandRoqueDeplaceAussiLaTour_RG2() {
        Plateau plateau = Positions.depuisFen("r3k3/8/8/8/8/8/8/4K3");

        jouer(plateau, "e8", "c8");

        assertInstanceOf(Tour.class, c(plateau, "d8").piece().orElseThrow());
    }

    @Test
    void pasDeRoqueSiLeRoiOuLaTourOntBouge_RG2() {
        Plateau plateau = Positions.depuisFen("4k3/8/8/8/8/8/8/4K2R");
        jouer(plateau, "h1", "h2");
        jouer(plateau, "e8", "e7");
        jouer(plateau, "h2", "h1");

        assertFalse(plateau.estLegal(c(plateau, "e1"), c(plateau, "g1")));
    }

    @Test
    void pasDeRoqueEnEchecNiATraversUneCaseAttaquee_RG2() {
        Plateau enEchec = Positions.depuisFen("4r2k/8/8/8/8/8/8/4K2R");
        assertFalse(enEchec.estLegal(c(enEchec, "e1"), c(enEchec, "g1")));

        Plateau traversee = Positions.depuisFen("5r1k/8/8/8/8/8/8/4K2R");
        assertFalse(traversee.estLegal(c(traversee, "e1"), c(traversee, "g1")));
    }

    @Test
    void pasDeGrandRoqueSiUnePieceEstEnB1MaisPossibleSiB1EstSeulementAttaquee_RG2() {
        Plateau bloque = Positions.depuisFen("7k/8/8/8/8/8/8/RN2K3");
        assertFalse(bloque.estLegal(c(bloque, "e1"), c(bloque, "c1")));

        Plateau attaquee = Positions.depuisFen("1r5k/8/8/8/8/8/8/R3K3");
        assertTrue(attaquee.estLegal(c(attaquee, "e1"), c(attaquee, "c1")));
    }

    @Test
    void laPriseEnPassantRetireLePionAdverse_RG2() {
        Plateau plateau = Positions.depuisFen("4k3/3p4/8/4P3/8/8/8/4K3");
        jouer(plateau, "d7", "d5");

        Optional<Piece> prise = plateau.deplacer(c(plateau, "e5"), c(plateau, "d6"), null);

        assertInstanceOf(Pion.class, prise.orElseThrow());
        assertTrue(c(plateau, "d5").estLibre());
    }

    @Test
    void laPriseEnPassantNeVautQueLeCoupSuivant_RG2() {
        Plateau plateau = Positions.depuisFen("4k3/3p4/8/4P3/8/8/8/4K3");
        jouer(plateau, "d7", "d5");
        jouer(plateau, "e1", "e2");
        jouer(plateau, "e8", "e7");

        assertFalse(plateau.estLegal(c(plateau, "e5"), c(plateau, "d6")));
    }

    @Test
    void laPromotionRemplaceLePionParLaPieceChoisie_RG2() {
        Plateau plateau = Positions.depuisFen("8/P6k/8/8/8/8/8/4K3");

        plateau.deplacer(c(plateau, "a7"), c(plateau, "a8"), TypePromotion.CAVALIER);

        assertInstanceOf(Cavalier.class, c(plateau, "a8").piece().orElseThrow());
    }

    @Test
    void uneArriveeSurLaDerniereRangeeExigeUnChoixDePromotion_RG2() {
        Plateau plateau = Positions.depuisFen("8/P6k/8/8/8/8/8/4K3");

        assertThrows(IllegalArgumentException.class, () -> jouer(plateau, "a7", "a8"));
    }

    @Test
    void unChoixDePromotionSansPromotionEstRefuse_RG2() {
        Plateau plateau = new Plateau();

        assertThrows(IllegalArgumentException.class,
                () -> plateau.deplacer(c(plateau, "e2"), c(plateau, "e4"), TypePromotion.DAME));
    }

    @Test
    void leMatEstReconnu_RG4() {
        Plateau plateau = Positions.depuisFen("R5k1/5ppp/8/8/8/8/8/4K3");

        assertEquals(Optional.of(MotifFin.MAT), plateau.situationFinale(Couleur.NOIR));
    }

    @Test
    void lePatEstReconnu_RG4() {
        Plateau plateau = Positions.depuisFen("7k/5Q2/6K1/8/8/8/8/8");

        assertEquals(Optional.of(MotifFin.PAT), plateau.situationFinale(Couleur.NOIR));
    }

    @Test
    void uneSituationOrdinaireNeTerminePasLaPartie() {
        assertEquals(Optional.empty(), new Plateau().situationFinale(Couleur.BLANC));
    }

    @Test
    void leMaterielInsuffisantEstReconnu_RG4() {
        assertEquals(Optional.of(MotifFin.MATERIEL_INSUFFISANT),
                Positions.depuisFen("4k3/8/8/8/8/8/8/4K3").situationFinale(Couleur.BLANC));
        assertEquals(Optional.of(MotifFin.MATERIEL_INSUFFISANT),
                Positions.depuisFen("4k3/8/8/8/8/8/8/2B1K3").situationFinale(Couleur.NOIR));
        assertEquals(Optional.of(MotifFin.MATERIEL_INSUFFISANT),
                Positions.depuisFen("2b1k3/8/8/8/8/8/8/5BK1").situationFinale(Couleur.BLANC));
    }

    @Test
    void deuxFousSurDesCasesDeCouleursDifferentesPeuventEncoreMater_RG4() {
        assertEquals(Optional.empty(), Positions.depuisFen("1b2k3/8/8/8/8/8/8/5BK1").situationFinale(Couleur.BLANC));
    }

    @Test
    void unRoiSeulOuAvecUnePieceLegereNePeutPasMater_RG5() {
        assertFalse(Positions.depuisFen("4k3/8/8/8/8/8/8/4K3").peutMater(Couleur.BLANC));
        assertFalse(Positions.depuisFen("4k3/8/8/8/8/8/8/2B1K3").peutMater(Couleur.BLANC));
        assertTrue(Positions.depuisFen("4k3/8/8/8/8/8/P7/4K3").peutMater(Couleur.BLANC));
    }

    @Test
    void laPositionSuitLeTraitLesDroitsDeRoqueEtLaPriseEnPassant() {
        Plateau plateau = new Plateau();
        jouer(plateau, "e2", "e4");
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3", plateau.position(Couleur.NOIR));

        jouer(plateau, "e7", "e5");
        jouer(plateau, "e1", "e2");
        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPPKPPP/RNBQ1BNR b kq -", plateau.position(Couleur.NOIR));
    }
}
