package echecs.controle;

import echecs.metier.joueur.Joueur;
import echecs.metier.partie.Partie;
import echecs.metier.partie.PartieEntreJoueurs;
import java.util.List;
import java.util.Optional;

/** Games being played stay in memory; finished games are stored for good. */
public interface DepotParties {
    void enregistrer(Partie partie);

    List<PartieEntreJoueurs> partiesOuvertes();

    Optional<Partie> partieActiveDe(Joueur joueur);

    List<Partie> partiesTermineesDe(Joueur joueur);
}
