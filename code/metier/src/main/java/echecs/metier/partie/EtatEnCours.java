package echecs.metier.partie;

import echecs.metier.jeu.Case;
import echecs.metier.jeu.Coup;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.MotifFin;
import echecs.metier.jeu.TypePromotion;
import echecs.metier.joueur.Joueur;
import java.util.Optional;

public class EtatEnCours extends EtatDePartie {
    // RG16: a game can be cancelled until each side has played its first move.
    private static final int COUPS_AVANT_FIN_DE_L_ANNULATION = 2;

    @Override
    public EtatPartie valeur() {
        return EtatPartie.EN_COURS;
    }

    @Override
    public void annuler(Partie partie, Joueur joueur) {
        if (!partie.estParticipant(joueur) || partie.nombreCoupsJoues() >= COUPS_AVANT_FIN_DE_L_ANNULATION) {
            throw refus("annuler");
        }
        partie.cloreSansResultat();
    }

    @Override
    public void annulerParLeSysteme(Partie partie) {
        partie.cloreSansResultat();
    }

    @Override
    public Coup jouerCoup(Partie partie, Joueur joueur, Case depart, Case arrivee, TypePromotion promotion) {
        Coup coup = partie.executerCoup(joueur, depart, arrivee, promotion);
        Optional<MotifFin> motif = partie.motifDeFin();
        motif.ifPresent(fin -> partie.terminer(partie.resultatDe(fin), fin));
        return coup;
    }

    @Override
    public void abandonner(Partie partie, Joueur joueur) {
        Couleur couleur = partie.couleurDe(joueur).orElseThrow(() -> refus("abandonner"));
        partie.terminer(Resultat.victoireDe(couleur.adverse()), MotifFin.ABANDON);
    }

    @Override
    public void proposerNulle(PartieEntreJoueurs partie, Joueur joueur) {
        Couleur couleur = partie.couleurDe(joueur).orElseThrow(() -> refus("proposer la nulle"));
        if (partie.nulleProposeePar().isPresent()) {
            throw refus("proposer la nulle");
        }
        partie.enregistrerPropositionNulle(couleur);
    }

    @Override
    public void accepterNulle(PartieEntreJoueurs partie, Joueur joueur) {
        if (!partie.aRecuUneProposition(joueur)) {
            throw refus("accepter la nulle");
        }
        partie.terminer(Resultat.NULLE, MotifFin.ACCORD);
    }

    @Override
    public void refuserNulle(PartieEntreJoueurs partie, Joueur joueur) {
        if (!partie.aRecuUneProposition(joueur)) {
            throw refus("refuser la nulle");
        }
        partie.enregistrerPropositionNulle(null);
    }

    @Override
    public void tempsEcoule(PartieEntreJoueurs partie) {
        Couleur vainqueur = partie.trait().adverse();
        Resultat resultat = partie.plateau().peutMater(vainqueur) ? Resultat.victoireDe(vainqueur) : Resultat.NULLE;
        partie.terminer(resultat, MotifFin.TEMPS_ECOULE);
    }
}
