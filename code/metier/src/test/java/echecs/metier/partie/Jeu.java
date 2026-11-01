package echecs.metier.partie;

import echecs.metier.jeu.Case;
import echecs.metier.jeu.Coup;
import echecs.metier.jeu.Plateau;
import echecs.metier.joueur.Joueur;

/** Lets the tests write moves as text: jouer(partie, alice, "e2", "e4"). */
final class Jeu {
    private Jeu() {
    }

    static Case caseDe(Plateau plateau, String nom) {
        return plateau.caseEn(nom.substring(0, 1), Integer.parseInt(nom.substring(1)));
    }

    static Coup jouer(Partie partie, Joueur joueur, String depart, String arrivee) {
        return partie.jouerCoup(joueur, caseDe(partie.plateau(), depart), caseDe(partie.plateau(), arrivee), null);
    }
}
