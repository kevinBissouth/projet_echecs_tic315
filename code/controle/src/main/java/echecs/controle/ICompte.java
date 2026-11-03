package echecs.controle;

import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.Joueur;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What the web site can ask about accounts. Managing accounts is reserved for an administrator (RG17). */
public interface ICompte {
    /** @throws CompteRefuseException if a field is invalid, or the pseudo or e-mail is taken (RG12) */
    Joueur creerCompte(String pseudo, String email, String motDePasse);

    /** First visit through the identity provider: the e-mail comes from its token, never from the browser. */
    Joueur creerCompteAvecIdentite(String jeton, String pseudo, String motDePasse);

    /** @throws ConnexionRefuseeException without saying which of the identifier or the password is wrong */
    Joueur seConnecter(String identifiant, String motDePasse);

    /**
     * @return the player with this verified identity, or empty on a first visit
     * @throws ConnexionRefuseeException if the token is not valid
     */
    Optional<Joueur> seConnecterAvecIdentite(String jeton);

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
