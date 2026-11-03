package echecs.ihm;

import echecs.controle.ICompte;
import echecs.metier.joueur.Joueur;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Back from Google: a known player is signed in at once; a new one still has to finish their account. */
@Component
public class ConnexionGoogle implements AuthenticationSuccessHandler {
    private static final String APRES_CONNEXION = "/#/salon";
    private static final String PREMIER_PASSAGE = "/#/connexion?google=nouveau";

    private final ICompte comptes;
    private final SessionJoueur session;

    public ConnexionGoogle(ICompte comptes, SessionJoueur session) {
        this.comptes = comptes;
        this.session = session;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest requete, HttpServletResponse reponse, Authentication authentification)
            throws IOException {
        OidcUser identite = (OidcUser) authentification.getPrincipal();
        String jeton = identite.getIdToken().getTokenValue();
        Optional<Joueur> joueur = comptes.seConnecterAvecIdentite(jeton);
        if (joueur.isPresent()) {
            session.ouvrir(joueur.get(), requete, reponse);
            reponse.sendRedirect(APRES_CONNEXION);
            return;
        }
        // Google's own session is dropped: until the account exists, this visitor is not a player.
        SecurityContextHolder.clearContext();
        requete.getSession().invalidate();
        requete.getSession(true).setAttribute(IdentiteGoogle.ATTRIBUT_DE_SESSION,
                new IdentiteGoogle(jeton, identite.getFullName(), identite.getEmail()));
        reponse.sendRedirect(PREMIER_PASSAGE);
    }
}
