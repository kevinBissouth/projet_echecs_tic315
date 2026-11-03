package echecs.ihm;

import echecs.controle.ControleurCompte;
import echecs.controle.ConnexionRefuseeException;
import echecs.controle.DepotJoueurs;
import echecs.controle.DepotParties;
import echecs.controle.FilesDeParties;
import echecs.controle.MoteurEchecs;
import echecs.controle.ServiceAuthentification;
import echecs.controle.ServiceMotsDePasse;
import echecs.infrastructure.AdaptateurOpenIdConnect;
import echecs.infrastructure.AdaptateurStockfish;
import echecs.infrastructure.DepotJoueursSql;
import echecs.infrastructure.DepotPartiesEnMemoire;
import echecs.infrastructure.MotsDePasseBcrypt;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Plugs the pieces together: only this class knows which adapter serves which interface. */
@SpringBootApplication
public class Application {
    private static final Duration TEMPS_DE_REFLEXION = Duration.ofSeconds(1);
    private static final Duration DELAI_DE_REPONSE = Duration.ofSeconds(15);
    private static final String CLES_GOOGLE = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String EMETTEUR_GOOGLE = "https://accounts.google.com";

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    MoteurEchecs moteurEchecs(@Value("${echecs.stockfish.executable}") String executable) {
        return new AdaptateurStockfish(executable, TEMPS_DE_REFLEXION, DELAI_DE_REPONSE);
    }

    @Bean
    ServiceMotsDePasse motsDePasse() {
        return new MotsDePasseBcrypt();
    }

    @Bean
    DepotJoueurs depotJoueurs(JdbcClient base, TransactionTemplate transaction) {
        return new DepotJoueursSql(base, transaction);
    }

    @Bean
    DepotParties depotParties() {
        return new DepotPartiesEnMemoire();
    }

    @Bean
    FilesDeParties filesDeParties() {
        return new FilesDeParties();
    }

    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }

    @Bean
    ServiceAuthentification authentification(@Value("${echecs.google.client-id:}") String idClient) {
        if (idClient.isEmpty()) {
            return jeton -> {
                throw new ConnexionRefuseeException("La connexion avec Google n'est pas configurée");
            };
        }
        return new AdaptateurOpenIdConnect(decodeurGoogle(idClient));
    }

    /** The token must come from Google, and be meant for this site and no other. */
    private static JwtDecoder decodeurGoogle(String idClient) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withJwkSetUri(CLES_GOOGLE).build();
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(EMETTEUR_GOOGLE),
                jeton -> jeton.getAudience().contains(idClient)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("audience_invalide"))));
        return decodeur;
    }

    @Bean
    ControleurCompte controleurCompte(ServiceAuthentification authentification, ServiceMotsDePasse motsDePasse,
                                      DepotJoueurs depotJoueurs, DepotParties depotParties, FilesDeParties files) {
        return new ControleurCompte(authentification, motsDePasse, depotJoueurs, depotParties, files);
    }

    /** The first administrator cannot be named by another: they come from the host's configuration (RG17). */
    @Bean
    ApplicationRunner administrateurInitial(ControleurCompte comptes,
                                            @Value("${echecs.admin.pseudo:}") String pseudo,
                                            @Value("${echecs.admin.email:}") String email) {
        return arguments -> {
            if (!pseudo.isEmpty() && !email.isEmpty()) {
                comptes.creerAdministrateurInitial(pseudo, email);
            }
        };
    }
}
