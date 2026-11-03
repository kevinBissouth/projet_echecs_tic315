package echecs.controle;

import echecs.metier.jeu.Coup;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.TypePromotion;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.Cadence;
import echecs.metier.partie.Partie;
import echecs.metier.partie.PartieEntreJoueurs;
import java.util.List;

/** What the web site can ask about games, for the connected player. */
public interface IPartie {
    /**
     * @param couleur the chosen colour, null for a random one
     * @param invite the only player allowed to join, null for a game open to all
     */
    PartieEntreJoueurs creerPartie(Cadence cadence, Couleur couleur, Joueur invite);

    List<PartieEntreJoueurs> consulterPartiesOuvertes();

    /** Asks to join: the game starts once its creator accepts (RG11, RG13). */
    void demanderARejoindre(PartieEntreJoueurs partie);

    /** The creator accepts the player who asked: the game starts. */
    void confirmerDemande(PartieEntreJoueurs partie);

    /** The creator declines the player who asked: the game is cancelled. */
    void refuserDemande(PartieEntreJoueurs partie);

    void retirerDemande(PartieEntreJoueurs partie);

    /** The creator withdraws one of their games that is still waiting. */
    void annulerPartie(PartieEntreJoueurs partie);

    void refuserInvitation(PartieEntreJoueurs partie);

    void annulerPartie();

    /**
     * @param depart the name of a square, such as "e2"
     * @param promotion the chosen piece, null when the move is not a promotion
     */
    Coup jouerCoup(String depart, String arrivee, TypePromotion promotion);

    void proposerNulle();

    void repondreNulle(boolean accepte);

    void abandonner();

    void demarrerPartieContreOrdinateur(int force, Couleur couleur);

    List<Coup> revoirPartie(Partie partie);
}
