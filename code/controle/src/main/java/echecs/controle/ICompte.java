package echecs.controle;

import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.Joueur;
import java.util.List;
import java.util.Map;

/** What the web site can ask about accounts. Who may call the administrator's actions is checked by ihm. */
public interface ICompte {
    Joueur creerCompte(String pseudo, String email);

    Joueur seConnecter(String jeton);

    Map<CategorieCadence, Classement> consulterClassements(Joueur joueur);

    List<Joueur> consulterComptes();

    void suspendre(Joueur joueur);

    void reactiver(Joueur joueur);
}
