package echecs.ihm;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/** Who may call what: every API address needs a session, except the few that open one. */
@Configuration
public class ConfigurationSecurite {
    private static final String ERREUR_GOOGLE = "/#/connexion?etat=google";

    @Bean
    SecurityFilterChain filtres(HttpSecurity http, ObjectProvider<ClientRegistrationRepository> google,
                                ConnexionGoogle connexionGoogle) throws Exception {
        http.authorizeHttpRequests(regles -> regles
                        .requestMatchers("/api/inscription", "/api/connexion", "/api/moi", "/api/google/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // The browser sends the session cookie by itself: this token proves the request comes from our pages.
                .csrf(csrf -> csrf.spa())
                .exceptionHandling(erreurs -> erreurs.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(sortie -> sortie.logoutUrl("/api/deconnexion")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        if (google.getIfAvailable() != null) {
            http.oauth2Login(connexion -> connexion.successHandler(connexionGoogle).failureUrl(ERREUR_GOOGLE));
        }
        return http.build();
    }

    /** Without the host's keys the site still works, with passwords only: the Google button is hidden. */
    @Bean
    @ConditionalOnExpression("!'${echecs.google.client-id:}'.isEmpty()")
    ClientRegistrationRepository google(@Value("${echecs.google.client-id}") String id,
                                        @Value("${echecs.google.client-secret}") String secret) {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(id)
                .clientSecret(secret)
                .scope("openid", "email", "profile")
                .build());
    }
}
