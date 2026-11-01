package echecs.metier.joueur;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class JoueurTest {
    private final Joueur alice = new Joueur("alice", "alice@exemple.com");

    @Test
    void unAdministrateurGereLesComptesMaisNeJouePas_RG17() {
        Joueur administrateur = Joueur.administrateur("admin", "admin@exemple.com");

        assertTrue(administrateur.estAdministrateur());
        assertFalse(administrateur.peutJouer());
        assertThrows(IllegalStateException.class, administrateur::occuper);
    }

    @Test
    void unJoueurNommeAdministrateurNeJouePlus_RG17() {
        alice.nommerAdministrateur();

        assertTrue(alice.estAdministrateur());
        assertFalse(alice.peutJouer());
    }

    @Test
    void unJoueurEnPartieNePeutPasEtreNomme_RG17() {
        alice.occuper();

        assertThrows(IllegalStateException.class, alice::nommerAdministrateur);
        assertFalse(alice.estAdministrateur());
    }

    @Test
    void unNouveauJoueurPeutJouer() {
        assertFalse(alice.estAdministrateur());
        assertEquals(EtatCompte.ACTIF, alice.etat());
        assertTrue(alice.peutJouer());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void unPseudoOuUnEmailVideEstRefuse(String valeurVide) {
        assertThrows(IllegalArgumentException.class, () -> new Joueur(valeurVide, "bob@exemple.com"));
        assertThrows(IllegalArgumentException.class, () -> new Joueur("bob", valeurVide));
    }

    @ParameterizedTest
    @EnumSource(CategorieCadence.class)
    void chaqueFamilleDeCadenceDemarreA1500Points(CategorieCadence categorie) {
        assertEquals(Classement.POINTS_INITIAUX, alice.classement(categorie).points());
    }

    @Test
    void modifierUnClassementNeChangePasLesAutres_RG6() {
        alice.classement(CategorieCadence.BLITZ).modifierPoints(9);

        assertEquals(1509, alice.classement(CategorieCadence.BLITZ).points());
        assertEquals(1500, alice.classement(CategorieCadence.BULLET).points());
        assertEquals(1500, alice.classement(CategorieCadence.RAPIDE).points());
    }

    @Test
    void unJoueurDejaEnPartieNePeutPasEnPrendreUneAutre_RG11() {
        alice.occuper();

        assertFalse(alice.peutJouer());
        assertThrows(IllegalStateException.class, alice::occuper);
    }

    @Test
    void unJoueurLibereDeSaPartiePeutRejouer_RG11() {
        alice.occuper();
        alice.liberer();

        assertTrue(alice.peutJouer());
    }

    @Test
    void unCompteSuspenduNePeutPasJouer_RG8() {
        alice.suspendre();

        assertEquals(EtatCompte.SUSPENDU, alice.etat());
        assertFalse(alice.peutJouer());
        assertThrows(IllegalStateException.class, alice::occuper);
    }

    @Test
    void unCompteReactiveRedevientJouable_RG8() {
        alice.suspendre();
        alice.reactiver();

        assertTrue(alice.peutJouer());
    }

    @Test
    void deuxObjetsDeMemePseudoSontLeMemeJoueur_RG12() {
        Joueur recharge = new Joueur("alice", "alice@exemple.com");

        assertEquals(alice, recharge);
        assertEquals(alice.hashCode(), recharge.hashCode());
        assertNotEquals(alice, new Joueur("bob", "bob@exemple.com"));
    }

    @Test
    void suspendreDeuxFoisOuReactiverUnCompteActifEstRefuse() {
        assertThrows(IllegalStateException.class, alice::reactiver);
        alice.suspendre();
        assertThrows(IllegalStateException.class, alice::suspendre);
    }
}
