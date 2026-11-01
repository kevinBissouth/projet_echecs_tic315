package echecs.metier.partie;

import echecs.metier.joueur.Joueur;

public class EtatEnAttente extends EtatDePartie {
    @Override
    public EtatPartie valeur() {
        return EtatPartie.EN_ATTENTE;
    }

    @Override
    public void rejoindre(PartieEntreJoueurs partie, Joueur joueur) {
        if (!partie.peutEtreRejointePar(joueur)) {
            throw refus("rejoindre");
        }
        partie.ajouterAdversaire(joueur);
        partie.demarrer();
    }

    @Override
    public void refuserInvitation(PartieEntreJoueurs partie, Joueur joueur) {
        if (!partie.estInvite(joueur)) {
            throw refus("refuser l'invitation");
        }
        partie.cloreSansResultat();
    }

    @Override
    public void annuler(Partie partie, Joueur joueur) {
        if (!partie.estParticipant(joueur)) {
            throw refus("annuler");
        }
        partie.cloreSansResultat();
    }

    @Override
    public void annulerParLeSysteme(Partie partie) {
        partie.cloreSansResultat();
    }
}
