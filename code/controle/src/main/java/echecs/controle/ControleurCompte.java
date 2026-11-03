package echecs.controle;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.EtatCompte;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.Partie;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ControleurCompte implements ICompte {
    private static final Pattern PSEUDO_VALIDE = Pattern.compile("[A-Za-z0-9_.-]{3,20}");
    private static final Pattern EMAIL_VALIDE = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern DEBUT_DE_PSEUDO = Pattern.compile("[A-Za-z0-9_.-]{1,20}");
    private static final int SUGGESTIONS_MAXIMUM = 8;
    // A few more are asked for: some of them are filtered out afterwards.
    private static final int SUGGESTIONS_DEMANDEES = 20;
    private static final int LONGUEUR_MINIMALE_MOT_DE_PASSE = 8;
    // BCrypt ignores what comes after 72 bytes: a longer password would only seem stronger.
    private static final int OCTETS_MAXIMUM_MOT_DE_PASSE = 72;

    private final ServiceAuthentification authentification;
    private final ServiceMotsDePasse motsDePasse;
    private final DepotJoueurs depotJoueurs;
    private final DepotParties depotParties;
    private final FilesDeParties files;
    // Compared when the identifier is unknown: otherwise the answer's speed would reveal who has an account.
    private final String empreinteFactice;

    public ControleurCompte(ServiceAuthentification authentification, ServiceMotsDePasse motsDePasse,
                            DepotJoueurs depotJoueurs, DepotParties depotParties, FilesDeParties files) {
        this.authentification = authentification;
        this.motsDePasse = motsDePasse;
        this.depotJoueurs = depotJoueurs;
        this.depotParties = depotParties;
        this.files = files;
        this.empreinteFactice = motsDePasse.hacher(UUID.randomUUID().toString());
    }

    /** RG12. The database's unique constraints remain the guarantee against two requests at once. */
    @Override
    public Joueur creerCompte(String pseudo, String email, String motDePasse) {
        exigerUnCompteValide(pseudo, email, motDePasse);
        String emailNormalise = normaliser(email);
        if (depotJoueurs.pseudoExiste(pseudo)) {
            throw new CompteRefuseException("Ce pseudo est déjà pris");
        }
        if (depotJoueurs.trouverParEmail(emailNormalise).isPresent()) {
            throw new CompteRefuseException("Cette adresse e-mail a déjà un compte");
        }
        Joueur joueur = new Joueur(pseudo, emailNormalise);
        depotJoueurs.creer(joueur, motsDePasse.hacher(motDePasse));
        return joueur;
    }

    @Override
    public Joueur creerCompteAvecIdentite(String jeton, String pseudo, String motDePasse) {
        return creerCompte(pseudo, authentification.verifierIdentite(jeton), motDePasse);
    }

    @Override
    public Joueur seConnecter(String identifiant, String motDePasse) {
        Optional<Joueur> joueur = chercher(identifiant);
        String empreinte = joueur.flatMap(depotJoueurs::empreinteDe).orElse(empreinteFactice);
        boolean correct = motDePasse != null && motsDePasse.correspond(motDePasse, empreinte);
        if (joueur.isEmpty() || !correct) {
            throw new ConnexionRefuseeException("Pseudo, e-mail ou mot de passe incorrect");
        }
        return joueur.get();
    }

    @Override
    public Optional<Joueur> seConnecterAvecIdentite(String jeton) {
        return depotJoueurs.trouverParEmail(normaliser(authentification.verifierIdentite(jeton)));
    }

    /** Neither oneself, nor an administrator, nor a suspended account: only players who can be invited. */
    @Override
    public List<Joueur> rechercherAdversaires(Joueur appelant, String debut) {
        if (debut == null || !DEBUT_DE_PSEUDO.matcher(debut.trim()).matches()) {
            return List.of();
        }
        return depotJoueurs.pseudosCommencantPar(debut.trim(), SUGGESTIONS_DEMANDEES).stream()
                .filter(joueur -> !joueur.equals(appelant))
                .filter(joueur -> !joueur.estAdministrateur() && joueur.etat() == EtatCompte.ACTIF)
                .limit(SUGGESTIONS_MAXIMUM)
                .toList();
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
        String emailNormalise = normaliser(email);
        if (depotJoueurs.trouverParEmail(emailNormalise).isEmpty()) {
            // A password nobody knows: this account signs in through the identity provider.
            depotJoueurs.creer(Joueur.administrateur(pseudo, emailNormalise), motsDePasse.hacher(UUID.randomUUID().toString()));
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

    private Optional<Joueur> chercher(String identifiant) {
        if (identifiant == null || identifiant.isBlank()) {
            return Optional.empty();
        }
        String nettoye = identifiant.trim();
        return nettoye.contains("@") ? depotJoueurs.trouverParEmail(normaliser(nettoye)) : depotJoueurs.trouverParPseudo(nettoye);
    }

    private static String normaliser(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void exigerUnCompteValide(String pseudo, String email, String motDePasse) {
        if (pseudo == null || !PSEUDO_VALIDE.matcher(pseudo).matches()) {
            throw new CompteRefuseException("Le pseudo doit contenir de 3 à 20 caractères : lettres, chiffres, point, tiret ou tiret bas");
        }
        if (email == null || !EMAIL_VALIDE.matcher(email.trim()).matches()) {
            throw new CompteRefuseException("Cette adresse e-mail n'est pas valide");
        }
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MINIMALE_MOT_DE_PASSE) {
            throw new CompteRefuseException("Le mot de passe doit contenir au moins " + LONGUEUR_MINIMALE_MOT_DE_PASSE + " caractères");
        }
        if (motDePasse.getBytes(StandardCharsets.UTF_8).length > OCTETS_MAXIMUM_MOT_DE_PASSE) {
            throw new CompteRefuseException("Ce mot de passe est trop long");
        }
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
