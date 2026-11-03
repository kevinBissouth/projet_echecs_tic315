package echecs.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import echecs.controle.ConnexionRefuseeException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

class AdaptateurOpenIdConnectTest {
    private static AdaptateurOpenIdConnect adaptateurQuiLit(Map<String, Object> reclamations) {
        return new AdaptateurOpenIdConnect(jeton -> new Jwt(jeton, Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), reclamations));
    }

    @Test
    void uneAdresseVerifieeEstRetenue() {
        var adaptateur = adaptateurQuiLit(Map.of("email", "alice@exemple.com", "email_verified", true));

        assertEquals("alice@exemple.com", adaptateur.verifierIdentite("jeton"));
    }

    @Test
    void uneAdresseNonVerifieeEstRefusee() {
        var adaptateur = adaptateurQuiLit(Map.of("email", "alice@exemple.com", "email_verified", false));

        assertThrows(ConnexionRefuseeException.class, () -> adaptateur.verifierIdentite("jeton"));
    }

    @Test
    void unJetonSansAdresseEstRefuse() {
        var adaptateur = adaptateurQuiLit(Map.of("sub", "123"));

        assertThrows(ConnexionRefuseeException.class, () -> adaptateur.verifierIdentite("jeton"));
    }

    @Test
    void unJetonFalsifieEstRefuse() {
        var adaptateur = new AdaptateurOpenIdConnect(jeton -> {
            throw new BadJwtException("signature invalide");
        });

        assertThrows(ConnexionRefuseeException.class, () -> adaptateur.verifierIdentite("faux"));
    }
}
