package echecs.metier.partie;

import echecs.metier.jeu.Case;
import echecs.metier.jeu.Coup;
import echecs.metier.jeu.TypePromotion;
import echecs.metier.joueur.Joueur;

/**
 * State pattern: one class per state of the state diagram, one operation per event. By default a
 * state refuses; each concrete state overrides only the events it accepts.
 */
public abstract class EtatDePartie {
    public abstract EtatPartie valeur();

    public void rejoindre(PartieEntreJoueurs partie, Joueur joueur) {
        throw refus("rejoindre");
    }

    public void refuserInvitation(PartieEntreJoueurs partie, Joueur joueur) {
        throw refus("refuser l'invitation");
    }

    public void annuler(Partie partie, Joueur joueur) {
        throw refus("annuler");
    }

    public void annulerParLeSysteme(Partie partie) {
        throw refus("annuler");
    }

    /** @param joueur the player making the move, null for the computer */
    public Coup jouerCoup(Partie partie, Joueur joueur, Case depart, Case arrivee, TypePromotion promotion) {
        throw refus("jouer un coup");
    }

    public void abandonner(Partie partie, Joueur joueur) {
        throw refus("abandonner");
    }

    public void proposerNulle(PartieEntreJoueurs partie, Joueur joueur) {
        throw refus("proposer la nulle");
    }

    public void accepterNulle(PartieEntreJoueurs partie, Joueur joueur) {
        throw refus("accepter la nulle");
    }

    public void refuserNulle(PartieEntreJoueurs partie, Joueur joueur) {
        throw refus("refuser la nulle");
    }

    public void tempsEcoule(PartieEntreJoueurs partie) {
        throw refus("temps écoulé");
    }

    protected final TransitionInterditeException refus(String evenement) {
        return new TransitionInterditeException(evenement, valeur());
    }
}
