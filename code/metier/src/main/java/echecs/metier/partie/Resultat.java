package echecs.metier.partie;

import echecs.metier.jeu.Couleur;

public enum Resultat {
    VICTOIRE_BLANCS,
    VICTOIRE_NOIRS,
    NULLE;

    static Resultat victoireDe(Couleur couleur) {
        return couleur == Couleur.BLANC ? VICTOIRE_BLANCS : VICTOIRE_NOIRS;
    }
}
