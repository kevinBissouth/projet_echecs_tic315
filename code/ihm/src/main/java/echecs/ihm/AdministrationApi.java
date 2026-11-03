package echecs.ihm;

import echecs.controle.CompteRefuseException;
import echecs.controle.ConnexionRefuseeException;
import echecs.controle.DepotJoueurs;
import echecs.controle.ICompte;
import echecs.metier.joueur.Joueur;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Account management (BF12). The controller checks the caller's role on every action (RG17). */
@RestController
class AdministrationApi {
    record VueCompte(String pseudo, String email, boolean administrateur, boolean suspendu) { }

    private final ICompte comptes;
    private final DepotJoueurs joueurs;

    AdministrationApi(ICompte comptes, DepotJoueurs joueurs) {
        this.comptes = comptes;
        this.joueurs = joueurs;
    }

    @GetMapping("/api/admin/comptes")
    List<VueCompte> comptes(Principal connecte) {
        return comptes.consulterComptes(appelant(connecte)).stream()
                .map(joueur -> new VueCompte(joueur.pseudo(), joueur.email(), joueur.estAdministrateur(), joueur.etat().name().equals("SUSPENDU")))
                .toList();
    }

    @PostMapping("/api/admin/comptes/{pseudo}/suspension")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void suspendre(@PathVariable String pseudo, Principal connecte) {
        Joueur appelant = appelant(connecte);
        agir(() -> comptes.suspendre(appelant, cible(pseudo)));
    }

    @PostMapping("/api/admin/comptes/{pseudo}/reactivation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactiver(@PathVariable String pseudo, Principal connecte) {
        Joueur appelant = appelant(connecte);
        agir(() -> comptes.reactiver(appelant, cible(pseudo)));
    }

    @PostMapping("/api/admin/comptes/{pseudo}/nomination")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void nommer(@PathVariable String pseudo, Principal connecte) {
        Joueur appelant = appelant(connecte);
        agir(() -> comptes.nommerAdministrateur(appelant, cible(pseudo)));
    }

    /** Already suspended, already active: the page was out of date, and the answer says so. */
    private static void agir(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException etatDejaAtteint) {
            throw new CompteRefuseException(etatDejaAtteint.getMessage());
        }
    }

    private Joueur appelant(Principal connecte) {
        return joueurs.trouverParPseudo(connecte.getName()).orElseThrow(() -> new ConnexionRefuseeException("Ce compte n'existe plus"));
    }

    private Joueur cible(String pseudo) {
        return joueurs.trouverParPseudo(pseudo).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
