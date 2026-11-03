package echecs.ihm;

import echecs.controle.ConnexionRefuseeException;
import echecs.controle.DepotJoueurs;
import echecs.controle.ICompte;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Joueur;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Translates HTTP into account actions, and back. The rules themselves are in the controller. */
@RestController
class CompteApi {
    record DemandeInscription(String pseudo, String email, String motDePasse) { }

    record DemandeConnexion(String identifiant, String motDePasse) { }

    record DemandeFinalisation(String pseudo, String motDePasse) { }

    record VueJoueur(String pseudo, boolean administrateur, Map<CategorieCadence, Integer> classements) { }

    record VueIdentite(String nom, String email) { }

    record VueSession(VueJoueur joueur, VueIdentite identiteEnAttente, boolean googleDisponible) { }

    private final ICompte comptes;
    private final DepotJoueurs joueurs;
    private final SessionJoueur session;
    private final LimiteurDeConnexions limiteur;
    private final boolean googleDisponible;

    CompteApi(ICompte comptes, DepotJoueurs joueurs, SessionJoueur session, LimiteurDeConnexions limiteur,
              ObjectProvider<ClientRegistrationRepository> google) {
        this.comptes = comptes;
        this.joueurs = joueurs;
        this.session = session;
        this.limiteur = limiteur;
        this.googleDisponible = google.getIfAvailable() != null;
    }

    /** Public on purpose: this is how a page learns whether its visitor is signed in. */
    @GetMapping("/api/moi")
    VueSession moi(Principal connecte, HttpServletRequest requete) {
        HttpSession sessionHttp = requete.getSession(false);
        IdentiteGoogle identite = sessionHttp == null ? null : (IdentiteGoogle) sessionHttp.getAttribute(IdentiteGoogle.ATTRIBUT_DE_SESSION);
        VueJoueur joueur = connecte == null ? null : joueurs.trouverParPseudo(connecte.getName()).map(this::vue).orElse(null);
        VueIdentite enAttente = identite == null ? null : new VueIdentite(identite.nom(), identite.email());
        return new VueSession(joueur, enAttente, googleDisponible);
    }

    @PostMapping("/api/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    VueJoueur inscrire(@RequestBody DemandeInscription demande, HttpServletRequest requete, HttpServletResponse reponse) {
        Joueur joueur = comptes.creerCompte(demande.pseudo(), demande.email(), demande.motDePasse());
        session.ouvrir(joueur, requete, reponse);
        return vue(joueur);
    }

    @PostMapping("/api/connexion")
    VueJoueur connecter(@RequestBody DemandeConnexion demande, HttpServletRequest requete, HttpServletResponse reponse) {
        if (limiteur.estBloque(demande.identifiant())) {
            throw new TropDeTentativesException();
        }
        Joueur joueur;
        try {
            joueur = comptes.seConnecter(demande.identifiant(), demande.motDePasse());
        } catch (ConnexionRefuseeException refus) {
            limiteur.signalerEchec(demande.identifiant());
            throw refus;
        }
        limiteur.signalerReussite(demande.identifiant());
        session.ouvrir(joueur, requete, reponse);
        return vue(joueur);
    }

    @PostMapping("/api/google/finalisation")
    @ResponseStatus(HttpStatus.CREATED)
    VueJoueur finaliser(@RequestBody DemandeFinalisation demande, HttpServletRequest requete, HttpServletResponse reponse) {
        HttpSession sessionHttp = requete.getSession(false);
        IdentiteGoogle identite = sessionHttp == null ? null : (IdentiteGoogle) sessionHttp.getAttribute(IdentiteGoogle.ATTRIBUT_DE_SESSION);
        if (identite == null) {
            throw new ConnexionRefuseeException("Reprenez la connexion avec Google");
        }
        Joueur joueur = comptes.creerCompteAvecIdentite(identite.jeton(), demande.pseudo(), demande.motDePasse());
        sessionHttp.removeAttribute(IdentiteGoogle.ATTRIBUT_DE_SESSION);
        session.ouvrir(joueur, requete, reponse);
        return vue(joueur);
    }

    @PostMapping("/api/google/annulation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void annuler(HttpServletRequest requete) {
        HttpSession sessionHttp = requete.getSession(false);
        if (sessionHttp != null) {
            sessionHttp.removeAttribute(IdentiteGoogle.ATTRIBUT_DE_SESSION);
        }
    }

    private VueJoueur vue(Joueur joueur) {
        Map<CategorieCadence, Integer> points = new EnumMap<>(CategorieCadence.class);
        comptes.consulterClassements(joueur).forEach((categorie, classement) -> points.put(categorie, classement.points()));
        return new VueJoueur(joueur.pseudo(), joueur.estAdministrateur(), points);
    }
}
