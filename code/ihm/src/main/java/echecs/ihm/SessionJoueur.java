package echecs.ihm;

import echecs.metier.joueur.Joueur;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Opens the session of a player, whichever way they proved who they are. */
@Component
public class SessionJoueur {
    private static final String ROLE_ADMINISTRATEUR = "ROLE_ADMINISTRATEUR";
    private static final String ROLE_JOUEUR = "ROLE_JOUEUR";

    private final SecurityContextRepository contextes = new HttpSessionSecurityContextRepository();

    public void ouvrir(Joueur joueur, HttpServletRequest requete, HttpServletResponse reponse) {
        // A new identifier at sign-in: one planted beforehand by an attacker becomes useless.
        if (requete.getSession(false) != null) {
            requete.changeSessionId();
        }
        String role = joueur.estAdministrateur() ? ROLE_ADMINISTRATEUR : ROLE_JOUEUR;
        SecurityContext contexte = SecurityContextHolder.createEmptyContext();
        contexte.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                joueur.pseudo(), null, List.of(new SimpleGrantedAuthority(role))));
        SecurityContextHolder.setContext(contexte);
        contextes.saveContext(contexte, requete, reponse);
    }
}
