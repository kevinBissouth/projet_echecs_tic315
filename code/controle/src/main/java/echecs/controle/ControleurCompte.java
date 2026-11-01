package echecs.controle;

import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.Partie;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ControleurCompte implements ICompte {
    private final ServiceAuthentification authentification;
    private final DepotJoueurs depotJoueurs;
    private final DepotParties depotParties;
    private final FilesDeParties files;

    public ControleurCompte(ServiceAuthentification authentification, DepotJoueurs depotJoueurs,
                            DepotParties depotParties, FilesDeParties files) {
        this.authentification = authentification;
        this.depotJoueurs = depotJoueurs;
        this.depotParties = depotParties;
        this.files = files;
    }

    /** RG12. The database's unique constraints remain the guarantee against two requests at once. */
    @Override
    public Joueur creerCompte(String pseudo, String email) {
        if (depotJoueurs.pseudoExiste(pseudo)) {
            throw new CompteRefuseException("Ce pseudo est déjà pris");
        }
        if (depotJoueurs.trouverParEmail(email).isPresent()) {
            throw new CompteRefuseException("Cette adresse e-mail a déjà un compte");
        }
        Joueur joueur = new Joueur(pseudo, email);
        depotJoueurs.enregistrer(joueur);
        return joueur;
    }

    @Override
    public Joueur seConnecter(String jeton) {
        String email = authentification.verifierIdentite(jeton);
        return depotJoueurs.trouverParEmail(email)
                .orElseThrow(() -> new ConnexionRefuseeException("Aucun compte pour cette identité"));
    }

    @Override
    public Map<CategorieCadence, Classement> consulterClassements(Joueur joueur) {
        Map<CategorieCadence, Classement> classements = new EnumMap<>(CategorieCadence.class);
        for (CategorieCadence categorie : CategorieCadence.values()) {
            classements.put(categorie, joueur.classement(categorie));
        }
        return classements;
    }

    /** The first administrator comes from the configuration: no administrator exists yet to name one. */
    public void creerAdministrateurInitial(String pseudo, String email) {
        if (depotJoueurs.trouverParEmail(email).isEmpty()) {
            depotJoueurs.enregistrer(Joueur.administrateur(pseudo, email));
        }
    }

    @Override
    public List<Joueur> consulterComptes(Joueur appelant) {
        exigerAdministrateur(appelant);
        return depotJoueurs.tous();
    }

    /** RG8, RG14: the account stays, and the player leaves their active game. */
    @Override
    public void suspendre(Joueur appelant, Joueur joueur) {
        exigerAdministrateur(appelant);
        if (joueur.estAdministrateur()) {
            throw new AccesRefuseException("Un administrateur ne peut pas être suspendu");
        }
        joueur.suspendre();
        depotJoueurs.enregistrer(joueur);
        depotParties.partieActiveDe(joueur).ifPresent(partie -> files.executer(partie, () -> retirer(joueur, partie)));
    }

    @Override
    public void reactiver(Joueur appelant, Joueur joueur) {
        exigerAdministrateur(appelant);
        joueur.reactiver();
        depotJoueurs.enregistrer(joueur);
    }

    @Override
    public void nommerAdministrateur(Joueur appelant, Joueur joueur) {
        exigerAdministrateur(appelant);
        try {
            joueur.nommerAdministrateur();
        } catch (IllegalStateException enPartie) {
            throw new AccesRefuseException(enPartie.getMessage());
        }
        depotJoueurs.enregistrer(joueur);
    }

    private static void exigerAdministrateur(Joueur appelant) {
        if (!appelant.estAdministrateur()) {
            throw new AccesRefuseException("Action réservée à un administrateur");
        }
    }

    private static void retirer(Joueur joueur, Partie partie) {
        if (partie.etat() == EtatPartie.EN_ATTENTE) {
            partie.annulerParLeSysteme();
        } else if (partie.etat() == EtatPartie.EN_COURS) {
            partie.abandonner(joueur);
        }
    }
}
