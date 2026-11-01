package echecs.controle;

import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.ObservateurPartie;
import echecs.metier.partie.Participation;
import echecs.metier.partie.Partie;

/** Stores a game and its players' ratings when it ends, and closes its queue. */
class ArchivagePartie implements ObservateurPartie {
    private final DepotParties depotParties;
    private final DepotJoueurs depotJoueurs;
    private final FilesDeParties files;

    ArchivagePartie(DepotParties depotParties, DepotJoueurs depotJoueurs, FilesDeParties files) {
        this.depotParties = depotParties;
        this.depotJoueurs = depotJoueurs;
        this.files = files;
    }

    @Override
    public void actualiser(Partie partie) {
        if (partie.etat() == EtatPartie.TERMINEE || partie.etat() == EtatPartie.ANNULEE) {
            depotParties.enregistrer(partie);
            partie.participations().stream().map(Participation::joueur).forEach(depotJoueurs::enregistrer);
            files.liberer(partie);
        }
    }
}
