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

    /** One request at a time: the creator answers it before another player can ask. */
    @Override
    public void demanderARejoindre(PartieEntreJoueurs partie, Joueur joueur) {
        if (!partie.peutEtreRejointePar(joueur) || partie.demandeur().isPresent()) {
            throw refus("demander à rejoindre");
        }
        partie.enregistrerDemande(joueur);
    }

    /** The creator agrees: the player who asked joins, and the game starts. */
    @Override
    public void confirmerDemande(PartieEntreJoueurs partie, Joueur joueur) {
        Joueur demandeur = partie.demandeur().orElseThrow(() -> refus("confirmer la demande"));
        if (!joueur.equals(partie.createur())) {
            throw refus("confirmer la demande");
        }
        partie.effacerDemande();
        rejoindre(partie, demandeur);
    }

    /** The creator declines: the game is cancelled, as he no longer wants to play it. */
    @Override
    public void refuserDemande(PartieEntreJoueurs partie, Joueur joueur) {
        if (!joueur.equals(partie.createur()) || partie.demandeur().isEmpty()) {
            throw refus("refuser la demande");
        }
        partie.cloreSansResultat();
    }

    @Override
    public void retirerDemande(PartieEntreJoueurs partie, Joueur joueur) {
        if (partie.demandeur().filter(joueur::equals).isEmpty()) {
            throw refus("retirer la demande");
        }
        partie.effacerDemande();
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
