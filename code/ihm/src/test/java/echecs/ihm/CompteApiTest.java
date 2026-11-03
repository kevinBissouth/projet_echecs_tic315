package echecs.ihm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import echecs.controle.DepotJoueurs;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Joueur;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** The whole chain, through real HTTP and a real PostgreSQL: cookies, anti-forgery token, constraints. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"echecs.admin.pseudo=directeur", "echecs.admin.email=directeur@exemple.com"})
class CompteApiTest {
    private static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:18-alpine");
    private static final String SECRET = "un-long-secret";

    static {
        BASE.start();
    }

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry proprietes) {
        proprietes.add("spring.datasource.url",
                () -> BASE.getJdbcUrl() + "&user=" + BASE.getUsername() + "&password=" + BASE.getPassword());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DepotJoueurs joueurs;

    @Autowired
    private JdbcClient sql;

    /** A browser: it keeps its cookies, and sends the anti-forgery token as our pages do. */
    private final class Navigateur {
        private final CookieManager cookies = new CookieManager();
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> lire(String chemin) throws IOException, InterruptedException {
            return client.send(HttpRequest.newBuilder(adresse(chemin)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> envoyer(String chemin, String json) throws IOException, InterruptedException {
            lire("/api/moi");
            String jeton = cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                    .map(HttpCookie::getValue)
                    .findFirst().orElse("");
            return envoyerAvecJeton(chemin, json, jeton);
        }

        HttpResponse<String> envoyerAvecJeton(String chemin, String json, String jeton) throws IOException, InterruptedException {
            return client.send(HttpRequest.newBuilder(adresse(chemin))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", jeton)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build(), HttpResponse.BodyHandlers.ofString());
        }

        private URI adresse(String chemin) {
            return URI.create("http://localhost:" + port + chemin);
        }
    }

    private static String inscription(String pseudo, String email) {
        return "{\"pseudo\":\"" + pseudo + "\",\"email\":\"" + email + "\",\"motDePasse\":\"" + SECRET + "\"}";
    }

    private static String connexion(String identifiant, String motDePasse) {
        return "{\"identifiant\":\"" + identifiant + "\",\"motDePasse\":\"" + motDePasse + "\"}";
    }

    @Test
    void unVisiteurNEstPasConnecte() throws Exception {
        HttpResponse<String> moi = new Navigateur().lire("/api/moi");

        assertEquals(200, moi.statusCode());
        assertTrue(moi.body().contains("\"joueur\":null"));
        assertFalse(moi.body().contains("jeton"));
    }

    @Test
    void lesAdressesDeLApiExigentUneSession() throws Exception {
        assertEquals(401, new Navigateur().lire("/api/parties").statusCode());
    }

    @Test
    void sInscrireOuvreLaSessionEtNeStockeQuUneEmpreinte() throws Exception {
        Navigateur navigateur = new Navigateur();

        HttpResponse<String> reponse = navigateur.envoyer("/api/inscription", inscription("ines", "Ines@Exemple.com"));

        assertEquals(201, reponse.statusCode());
        assertFalse(reponse.body().contains(SECRET));
        assertTrue(navigateur.lire("/api/moi").body().contains("\"pseudo\":\"ines\""));
        String empreinte = sql.sql("select empreinte_mot_de_passe from joueurs where pseudo = 'ines'").query(String.class).single();
        assertTrue(empreinte.startsWith("$2"), "une empreinte BCrypt");
        assertFalse(empreinte.contains(SECRET));
    }

    @Test
    void sansJetonAntiFalsificationLaDemandeEstRefusee() throws Exception {
        assertEquals(403, new Navigateur().envoyerAvecJeton("/api/inscription", inscription("hugo", "hugo@exemple.com"), "faux").statusCode());
    }

    @Test
    void unPseudoOuUnEmailDejaPrisEstRefuse_RG12() throws Exception {
        new Navigateur().envoyer("/api/inscription", inscription("lea", "lea@exemple.com"));

        assertEquals(409, new Navigateur().envoyer("/api/inscription", inscription("LEA", "autre@exemple.com")).statusCode());
        assertEquals(409, new Navigateur().envoyer("/api/inscription", inscription("lea2", "LEA@exemple.com")).statusCode());
    }

    @Test
    void uneInscriptionInvalideEstRefusee() throws Exception {
        assertEquals(409, new Navigateur().envoyer("/api/inscription", inscription("<b>", "b@exemple.com")).statusCode());
        assertEquals(409, new Navigateur().envoyer("/api/inscription", "{}").statusCode());
    }

    @Test
    void onSeConnecteEtOnSeDeconnecte() throws Exception {
        new Navigateur().envoyer("/api/inscription", inscription("marc", "marc@exemple.com"));
        Navigateur navigateur = new Navigateur();

        assertEquals(200, navigateur.envoyer("/api/connexion", connexion("marc@exemple.com", SECRET)).statusCode());
        assertTrue(navigateur.lire("/api/moi").body().contains("\"pseudo\":\"marc\""));

        assertEquals(204, navigateur.envoyer("/api/deconnexion", "{}").statusCode());
        assertTrue(navigateur.lire("/api/moi").body().contains("\"joueur\":null"));
    }

    @Test
    void unMauvaisMotDePasseEstRefuse() throws Exception {
        new Navigateur().envoyer("/api/inscription", inscription("nora", "nora@exemple.com"));

        assertEquals(401, new Navigateur().envoyer("/api/connexion", connexion("nora", "mauvais-secret")).statusCode());
    }

    @Test
    void lesTentativesRepeteesSontBloquees() throws Exception {
        new Navigateur().envoyer("/api/inscription", inscription("omar", "omar@exemple.com"));
        Navigateur navigateur = new Navigateur();
        for (int tentative = 0; tentative < 5; tentative++) {
            assertEquals(401, navigateur.envoyer("/api/connexion", connexion("omar", "mauvais-secret")).statusCode());
        }

        assertEquals(429, navigateur.envoyer("/api/connexion", connexion("omar", SECRET)).statusCode());
    }

    @Test
    void finaliserSansIdentiteGoogleEstRefuse() throws Exception {
        assertEquals(401, new Navigateur().envoyer("/api/google/finalisation", "{\"pseudo\":\"paul\",\"motDePasse\":\"" + SECRET + "\"}").statusCode());
    }

    @Test
    void lAdministrateurInitialExisteAuDemarrage_RG17() {
        assertTrue(joueurs.trouverParEmail("directeur@exemple.com").orElseThrow().estAdministrateur());
    }

    @Test
    void unMemeJoueurResteLeMemeObjet_RG11() throws Exception {
        new Navigateur().envoyer("/api/inscription", inscription("rita", "rita@exemple.com"));

        Joueur parPseudo = joueurs.trouverParPseudo("RITA").orElseThrow();

        assertSame(parPseudo, joueurs.trouverParEmail("rita@exemple.com").orElseThrow());
        assertTrue(joueurs.tous().stream().anyMatch(joueur -> joueur == parPseudo));
    }

    @Test
    void lesPointsSontEnregistres_RG6() throws Exception {
        new Navigateur().envoyer("/api/inscription", inscription("sami", "sami@exemple.com"));
        Joueur sami = joueurs.trouverParPseudo("sami").orElseThrow();

        sami.classement(CategorieCadence.BLITZ).modifierPoints(9);
        joueurs.enregistrer(sami);

        int points = sql.sql("select points from classements where pseudo = 'sami' and categorie = 'BLITZ'").query(Integer.class).single();
        assertEquals(1509, points);
    }
}
