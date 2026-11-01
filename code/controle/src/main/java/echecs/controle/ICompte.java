package echecs.controle;

import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.Joueur;
import java.util.List;
import java.util.Map;

/** What the web site can ask about accounts. Managing accounts is reserved for an administrator (RG17). */
public interface ICompte {
    Joueur creerCompte(String pseudo, String email);

    Joueur seConnecter(String jeton);

    Map<CategorieCadence, Classement> consulterClassements(Joueur joueur);

    /** @throws AccesRefuseException if the caller is not an administrator */
    List<Joueur> consulterComptes(Joueur appelant);

    /** @throws AccesRefuseException if the caller is not an administrator, or targets an administrator */
    void suspendre(Joueur appelant, Joueur joueur);

    /** @throws AccesRefuseException if the caller is not an administrator */
    void reactiver(Joueur appelant, Joueur joueur);

    /** @throws AccesRefuseException if the caller is not an administrator, or the player is in a game */
    void nommerAdministrateur(Joueur appelant, Joueur joueur);
}
