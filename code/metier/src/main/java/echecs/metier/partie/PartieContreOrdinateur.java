package echecs.metier.partie;

import echecs.metier.jeu.Case;
import echecs.metier.jeu.Coup;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.TypePromotion;
import echecs.metier.joueur.Joueur;
import java.time.Clock;

/** A game with no clock (RG10) and no rating (RG6): it starts at once, against the engine. */
public class PartieContreOrdinateur extends Partie {
    private final Couleur couleurDuJoueur;
    private final int force;

    public PartieContreOrdinateur(Joueur joueur, Couleur couleur, int force, Clock horloge) {
        super(horloge);
        this.couleurDuJoueur = couleur;
        this.force = force;
        ajouterParticipation(new Participation(joueur, couleur, null));
        demarrer();
    }

    public int force() {
        return force;
    }

    public boolean ordinateurALeTrait() {
        return trait() != couleurDuJoueur;
    }

    /** The computer's move goes through the same checks as a player's, so an illegal one is refused. */
    public Coup jouerCoupOrdinateur(Case depart, Case arrivee, TypePromotion promotion) {
        if (!ordinateurALeTrait()) {
            throw new CoupRefuseException("Ce n'est pas le tour de l'ordinateur");
        }
        Coup coup = etatCourant().jouerCoup(this, null, depart, arrivee, promotion);
        notifierObservateurs();
        return coup;
    }
}
