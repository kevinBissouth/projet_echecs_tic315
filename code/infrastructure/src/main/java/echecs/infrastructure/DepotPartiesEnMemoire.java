package echecs.infrastructure;

import echecs.controle.DepotParties;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.Participation;
import echecs.metier.partie.Partie;
import echecs.metier.partie.PartieEntreJoueurs;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Games being played live in memory, as decided: a server restart loses them. */
public class DepotPartiesEnMemoire implements DepotParties {
    private final Set<Partie> parties = new LinkedHashSet<>();

    @Override
    public synchronized void enregistrer(Partie partie) {
        parties.add(partie);
    }

    @Override
    public synchronized List<PartieEntreJoueurs> partiesOuvertes() {
        return parties.stream()
                .filter(partie -> partie.etat() == EtatPartie.EN_ATTENTE)
                .filter(PartieEntreJoueurs.class::isInstance)
                .map(PartieEntreJoueurs.class::cast)
                .toList();
    }

    @Override
    public synchronized Optional<Partie> partieActiveDe(Joueur joueur) {
        return parties.stream()
                .filter(partie -> partie.etat() == EtatPartie.EN_ATTENTE || partie.etat() == EtatPartie.EN_COURS)
                .filter(partie -> participe(partie, joueur))
                .findFirst();
    }

    @Override
    public synchronized List<Partie> partiesTermineesDe(Joueur joueur) {
        return parties.stream()
                .filter(partie -> partie.etat() == EtatPartie.TERMINEE)
                .filter(partie -> participe(partie, joueur))
                .toList();
    }

    private static boolean participe(Partie partie, Joueur joueur) {
        return partie.participations().stream().map(Participation::joueur).anyMatch(joueur::equals);
    }
}
