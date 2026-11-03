package echecs.ihm;

import echecs.controle.ActionRefuseeException;
import echecs.controle.ControleurPartie;
import echecs.controle.DepotJoueurs;
import echecs.controle.DepotParties;
import echecs.controle.FilesDeParties;
import echecs.ihm.PresentationPartie.VuePartie;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.TypePromotion;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.Cadence;
import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.Partie;
import echecs.metier.partie.PartieEntreJoueurs;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Translates HTTP into game actions. The rules are in the controller and the business core. */
@RestController
class PartieApi {
    private static final int ELO_MINIMUM = 100;
    private static final int ELO_MAXIMUM = 3190;

    record DemandePartie(String cadence, String couleur, String invite) { }

    record DemandeOrdinateur(Integer elo, String couleur) { }

    record DemandeCoup(String depart, String arrivee, String promotion) { }

    record ReponseNulle(boolean accepte) { }

    /** A waiting game. Unavailable ones stay listed, greyed: their creator is away, in a game, or answering someone. */
    record VueOuverte(String id, String pseudo, Integer classement, String cadence, CategorieCadence categorie, String couleur,
                      String invite, long expirationMillisecondes, boolean enLigne, boolean occupe, boolean sollicitee) { }

    record VueSalon(List<VueOuverte> ouvertes, List<VueOuverte> invitations, List<VueOuverte> mesParties, VuePartie partieCourante) { }

    private final ControleursDeJoueurs controleurs;
    private final DepotJoueurs joueurs;
    private final DepotParties parties;
    private final FilesDeParties files;
    private final PresentationPartie presentation;
    private final DiffusionParties diffusion;

    PartieApi(ControleursDeJoueurs controleurs, DepotJoueurs joueurs, DepotParties parties, FilesDeParties files,
              PresentationPartie presentation, DiffusionParties diffusion) {
        this.controleurs = controleurs;
        this.joueurs = joueurs;
        this.parties = parties;
        this.files = files;
        this.presentation = presentation;
        this.diffusion = diffusion;
    }

    @GetMapping("/api/parties")
    VueSalon salon(Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        List<VueOuverte> ouvertes = new ArrayList<>();
        List<VueOuverte> invitations = new ArrayList<>();
        List<VueOuverte> miennes = new ArrayList<>();
        for (PartieEntreJoueurs partie : parties.partiesOuvertes()) {
            if (partie.createur().equals(joueur)) {
                miennes.add(vueOuverte(partie));
            } else if (partie.invite().isEmpty()) {
                ouvertes.add(vueOuverte(partie));
            } else if (partie.invite().get().equals(joueur)) {
                invitations.add(vueOuverte(partie));
            }
        }
        VuePartie courante = vueCourante(joueur)
                .filter(vue -> vue.etat().equals(EtatPartie.EN_ATTENTE.name()) || vue.etat().equals(EtatPartie.EN_COURS.name()))
                .orElse(null);
        return new VueSalon(ouvertes, invitations, miennes, courante);
    }

    @PostMapping("/api/parties")
    @ResponseStatus(HttpStatus.CREATED)
    VuePartie creer(@RequestBody DemandePartie demande, Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        Joueur invite = demande.invite() == null || demande.invite().isBlank() ? null : joueurs.trouverParPseudo(demande.invite().trim())
                .orElseThrow(() -> new ActionRefuseeException("Aucun joueur ne porte ce pseudo"));
        return vue(controleurs.de(joueur).creerPartie(cadence(demande.cadence()), couleur(demande.couleur()), invite), joueur);
    }

    @PostMapping("/api/parties/ordinateur")
    @ResponseStatus(HttpStatus.CREATED)
    VuePartie contreOrdinateur(@RequestBody DemandeOrdinateur demande, Principal connecte) {
        if (demande.elo() == null || demande.elo() < ELO_MINIMUM || demande.elo() > ELO_MAXIMUM) {
            throw new ActionRefuseeException("L'Elo de l'ordinateur doit être compris entre " + ELO_MINIMUM + " et " + ELO_MAXIMUM);
        }
        Joueur joueur = joueurDe(connecte);
        Couleur couleur = Optional.ofNullable(couleur(demande.couleur()))
                .orElseGet(() -> ThreadLocalRandom.current().nextBoolean() ? Couleur.BLANC : Couleur.NOIR);
        ControleurPartie controleur = controleurs.de(joueur);
        controleur.demarrerPartieContreOrdinateur(demande.elo(), couleur);
        return courante(controleur, joueur);
    }

    /** The creator must be there to accept: a request to someone who closed the site would wait for nothing. */
    @PostMapping("/api/parties/{id}/demande")
    VuePartie demander(@PathVariable UUID id, Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        PartieEntreJoueurs partie = ouverte(id);
        if (!diffusion.estEnLigne(partie.createur().pseudo())) {
            throw new ActionRefuseeException(partie.createur().pseudo() + " n'est plus en ligne");
        }
        ControleurPartie controleur = controleurs.de(joueur);
        controleur.demanderARejoindre(partie);
        return courante(controleur, joueur);
    }

    @DeleteMapping("/api/parties/{id}/demande")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retirerDemande(@PathVariable UUID id, Principal connecte) {
        controleurs.de(joueurDe(connecte)).retirerDemande(ouverte(id));
    }

    @PostMapping("/api/parties/{id}/demande/acceptation")
    VuePartie accepterDemande(@PathVariable UUID id, Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        ControleurPartie controleur = controleurs.de(joueur);
        controleur.confirmerDemande(ouverte(id));
        return courante(controleur, joueur);
    }

    @PostMapping("/api/parties/{id}/demande/refus")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void refuserDemande(@PathVariable UUID id, Principal connecte) {
        controleurs.de(joueurDe(connecte)).refuserDemande(ouverte(id));
    }

    @PostMapping("/api/parties/{id}/annulation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void annulerEnAttente(@PathVariable UUID id, Principal connecte) {
        controleurs.de(joueurDe(connecte)).annulerPartie(ouverte(id));
    }

    @PostMapping("/api/parties/{id}/refus")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void refuser(@PathVariable UUID id, Principal connecte) {
        controleurs.de(joueurDe(connecte)).refuserInvitation(ouverte(id));
    }

    @GetMapping("/api/partie")
    ResponseEntity<VuePartie> partie(Principal connecte) {
        return vueCourante(joueurDe(connecte)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** A request that ran out leaves the player outside the game: it is no longer theirs to show. */
    private Optional<VuePartie> vueCourante(Joueur joueur) {
        return controleurs.de(joueur).partieCourante()
                .map(partie -> vue(partie, joueur))
                .filter(vue -> vue.moi() != null || vue.maDemande());
    }

    @PostMapping("/api/partie/coups")
    VuePartie jouer(@RequestBody DemandeCoup coup, Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        ControleurPartie controleur = controleurs.de(joueur);
        controleur.jouerCoup(coup.depart(), coup.arrivee(), promotion(coup.promotion()));
        return courante(controleur, joueur);
    }

    @PostMapping("/api/partie/abandon")
    VuePartie abandonner(Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        ControleurPartie controleur = controleurs.de(joueur);
        controleur.abandonner();
        return courante(controleur, joueur);
    }

    @PostMapping("/api/partie/annulation")
    VuePartie annuler(Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        ControleurPartie controleur = controleurs.de(joueur);
        controleur.annulerPartie();
        return courante(controleur, joueur);
    }

    @PostMapping("/api/partie/nulle")
    VuePartie proposerNulle(Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        ControleurPartie controleur = controleurs.de(joueur);
        controleur.proposerNulle();
        return courante(controleur, joueur);
    }

    @PostMapping("/api/partie/nulle/reponse")
    VuePartie repondreNulle(@RequestBody ReponseNulle reponse, Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        ControleurPartie controleur = controleurs.de(joueur);
        controleur.repondreNulle(reponse.accepte());
        return courante(controleur, joueur);
    }

    private VuePartie courante(ControleurPartie controleur, Joueur joueur) {
        return vue(controleur.partieCourante().orElseThrow(() -> new ActionRefuseeException("Aucune partie en cours")), joueur);
    }

    private VuePartie vue(Partie partie, Joueur joueur) {
        return files.executer(partie, () -> presentation.pour(partie, joueur));
    }

    private Joueur joueurDe(Principal connecte) {
        return joueurs.trouverParPseudo(connecte.getName()).orElseThrow(() -> new ActionRefuseeException("Ce compte n'existe plus"));
    }

    private PartieEntreJoueurs ouverte(UUID id) {
        return parties.partiesOuvertes().stream().filter(partie -> partie.id().equals(id)).findFirst()
                .orElseThrow(() -> new ActionRefuseeException("Cette partie n'est plus ouverte"));
    }

    private VueOuverte vueOuverte(PartieEntreJoueurs partie) {
        return files.executer(partie, () -> {
            Cadence cadence = partie.cadence();
            Joueur createur = partie.createur();
            String couleur = partie.participations().getFirst().couleur().map(Enum::name).orElse(null);
            return new VueOuverte(partie.id().toString(), createur.pseudo(), createur.classement(cadence.categorie()).points(),
                    texte(cadence), cadence.categorie(), couleur, partie.invite().map(Joueur::pseudo).orElse(null),
                    presentation.restantAvant(partie.expireLe()), diffusion.estEnLigne(createur.pseudo()),
                    !createur.peutJouer(), partie.demandeur().isPresent());
        });
    }

    private static Cadence cadence(String texte) {
        return Cadence.DISPONIBLES.stream().filter(cadence -> texte(cadence).equals(texte)).findFirst()
                .orElseThrow(() -> new ActionRefuseeException("Cette cadence n'existe pas"));
    }

    private static String texte(Cadence cadence) {
        return cadence.tempsDeBase().toMinutes() + "+" + cadence.increment().toSeconds();
    }

    private static Couleur couleur(String texte) {
        if (texte == null || texte.isBlank() || texte.equals("HASARD")) {
            return null;
        }
        return enumeration(Couleur.class, texte, "Cette couleur n'existe pas");
    }

    private static TypePromotion promotion(String texte) {
        return texte == null || texte.isBlank() ? null : enumeration(TypePromotion.class, texte, "Cette pièce de promotion n'existe pas");
    }

    private static <E extends Enum<E>> E enumeration(Class<E> type, String texte, String refus) {
        try {
            return Enum.valueOf(type, texte);
        } catch (IllegalArgumentException inconnu) {
            throw new ActionRefuseeException(refus);
        }
    }
}
