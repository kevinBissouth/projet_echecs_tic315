package echecs.infrastructure;

import echecs.controle.ConnexionRefuseeException;
import echecs.controle.ServiceAuthentification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** Adapter pattern: translates {@link ServiceAuthentification} into OpenID Connect, by checking the signed ID token. */
public class AdaptateurOpenIdConnect implements ServiceAuthentification {
    private static final String RECLAMATION_EMAIL = "email";
    private static final String RECLAMATION_EMAIL_VERIFIE = "email_verified";

    private final JwtDecoder decodeur;

    /** @param decodeur checks the signature, the expiry, the issuer and that the token is meant for this site */
    public AdaptateurOpenIdConnect(JwtDecoder decodeur) {
        this.decodeur = decodeur;
    }

    @Override
    public String verifierIdentite(String jeton) {
        Jwt identite = decoder(jeton);
        String email = identite.getClaimAsString(RECLAMATION_EMAIL);
        // An unverified address could be someone else's: it must not open that person's account.
        if (email == null || !Boolean.TRUE.equals(identite.getClaimAsBoolean(RECLAMATION_EMAIL_VERIFIE))) {
            throw new ConnexionRefuseeException("Cette adresse e-mail n'est pas vérifiée");
        }
        return email;
    }

    private Jwt decoder(String jeton) {
        try {
            return decodeur.decode(jeton);
        } catch (JwtException refus) {
            throw new ConnexionRefuseeException("Identité refusée");
        }
    }
}
