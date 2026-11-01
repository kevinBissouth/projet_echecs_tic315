package echecs.controle;

import echecs.metier.joueur.Joueur;
import java.util.List;
import java.util.Optional;

/**
 * Must return the same object for the same player while the server runs: a player's "busy" flag
 * lives in memory only, and two objects would each believe the player is free (RG11).
 */
public interface DepotJoueurs {
    void enregistrer(Joueur joueur);

    Optional<Joueur> trouverParEmail(String email);

    boolean pseudoExiste(String pseudo);

    List<Joueur> tous();
}
