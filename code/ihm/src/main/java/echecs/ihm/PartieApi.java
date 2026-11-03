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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    record VueOuverte(String id, String pseudo, Integer classement, String cadence, CategorieCadence categorie, String couleur) { }

    record VueSalon(List<VueOuverte> ouvertes, List<VueOuverte> invitations, VuePartie partieCourante) { }

    private final ControleursDeJoueurs controleurs;
    private final DepotJoueurs joueurs;
    private final DepotParties parties;
    private final FilesDeParties files;
    private final PresentationPartie presentation;

    PartieApi(ControleursDeJoueurs controleurs, DepotJoueurs joueurs, DepotParties parties, FilesDeParties files, PresentationPartie presentation) {
        this.controleurs = controleurs;
        this.joueurs = joueurs;
        this.parties = parties;
        this.files = files;
        this.presentation = presentation;
    }

    @GetMapping("/api/parties")
    VueSalon salon(Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        List<PartieEntreJoueurs> ouvertes = parties.partiesOuvertes().stream().filter(partie -> !partie.createur().equals(joueur)).toList();
        VuePartie courante = controleurs.de(joueur).partieCourante()
                .filter(partie -> partie.etat() == EtatPartie.EN_ATTENTE || partie.etat() == EtatPartie.EN_COURS)
                .map(partie -> vue(partie, joueur))
                .orElse(null);
        return new VueSalon(
                ouvertes.stream().filter(partie -> partie.invite().isEmpty()).map(PartieApi::vueOuverte).toList(),
                ouvertes.stream().filter(partie -> partie.invite().filter(joueur::equals).isPresent()).map(PartieApi::vueOuverte).toList(),
                courante);
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

    @PostMapping("/api/parties/{id}/rejoindre")
    VuePartie rejoindre(@PathVariable UUID id, Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        ControleurPartie controleur = controleurs.de(joueur);
        controleur.rejoindrePartie(ouverte(id));
        return courante(controleur, joueur);
    }

    @PostMapping("/api/parties/{id}/refus")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void refuser(@PathVariable UUID id, Principal connecte) {
        controleurs.de(joueurDe(connecte)).refuserInvitation(ouverte(id));
    }

    @GetMapping("/api/partie")
    ResponseEntity<VuePartie> partie(Principal connecte) {
        Joueur joueur = joueurDe(connecte);
        return controleurs.de(joueur).partieCourante()
                .map(partie -> ResponseEntity.ok(vue(partie, joueur)))
                .orElseGet(() -> ResponseEntity.noContent().build());
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

    private static VueOuverte vueOuverte(PartieEntreJoueurs partie) {
        Cadence cadence = partie.cadence();
        String couleur = partie.participations().getFirst().couleur().map(Enum::name).orElse(null);
        return new VueOuverte(partie.id().toString(), partie.createur().pseudo(), partie.createur().classement(cadence.categorie()).points(),
                texte(cadence), cadence.categorie(), couleur);
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
