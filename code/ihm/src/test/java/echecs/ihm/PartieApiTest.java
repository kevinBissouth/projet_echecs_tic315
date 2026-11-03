package echecs.ihm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A real game between two browsers, through HTTP, the WebSocket and PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"echecs.admin.pseudo=directrice", "echecs.admin.email=directrice@exemple.com"})
class PartieApiTest {
    private static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:18-alpine");
    private static final String SECRET = "un-long-secret";
    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    private final class Joueur {
        final CookieManager cookies = new CookieManager();
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        final String pseudo;

        Joueur(String prefixe) throws Exception {
            pseudo = prefixe + UUID.randomUUID().toString().substring(0, 6);
            assertEquals(201, envoyer("/api/inscription", "{\"pseudo\":\"" + pseudo + "\",\"email\":\"" + pseudo + "@exemple.com\",\"motDePasse\":\"" + SECRET + "\"}").statusCode());
        }

        HttpResponse<String> lire(String chemin) throws IOException, InterruptedException {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> envoyer(String chemin, String json) throws IOException, InterruptedException {
            lire("/api/moi");
            String jeton = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElse("");
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                    .header("Content-Type", "application/json").header("X-XSRF-TOKEN", jeton)
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonNode partie() throws Exception {
            return JSON.readTree(lire("/api/partie").body());
        }

        HttpResponse<String> jouer(String depart, String arrivee) throws Exception {
            return envoyer("/api/partie/coups", "{\"depart\":\"" + depart + "\",\"arrivee\":\"" + arrivee + "\"}");
        }
    }

    private Joueur[] partieCommencee() throws Exception {
        Joueur createur = new Joueur("crea");
        Joueur adversaire = new Joueur("adve");
        assertEquals(201, createur.envoyer("/api/parties", "{\"cadence\":\"5+0\",\"couleur\":\"BLANC\"}").statusCode());
        JsonNode salon = JSON.readTree(adversaire.lire("/api/parties").body());
        String id = null;
        for (JsonNode ouverte : salon.get("ouvertes")) {
            if (ouverte.get("pseudo").asString().equals(createur.pseudo)) {
                id = ouverte.get("id").asString();
            }
        }
        assertEquals(200, adversaire.envoyer("/api/parties/" + id + "/rejoindre", "{}").statusCode());
        return new Joueur[] {createur, adversaire};
    }

    @Test
    void unePartieJusquAuMatEstArchiveeEtClassee() throws Exception {
        Joueur[] joueurs = partieCommencee();
        Joueur blancs = joueurs[0];
        Joueur noirs = joueurs[1];
        assertEquals("EN_COURS", blancs.partie().get("etat").asString());
        assertEquals(20, nombreDeCoupsLegaux(blancs.partie()));
        assertEquals(0, nombreDeCoupsLegaux(noirs.partie()));

        // Le mat du berger inversé : deux coups des blancs, deux des noirs.
        assertEquals(200, blancs.jouer("f2", "f3").statusCode());
        assertEquals(200, noirs.jouer("e7", "e5").statusCode());
        assertEquals(200, blancs.jouer("g2", "g4").statusCode());
        assertEquals(200, noirs.jouer("d8", "h4").statusCode());

        JsonNode fin = blancs.partie();
        assertEquals("TERMINEE", fin.get("etat").asString());
        assertEquals("perdue", fin.get("issue").asString());
        assertEquals("MAT", fin.get("motif").asString());
        assertEquals("gagnee", noirs.partie().get("issue").asString());

        JsonNode parties = JSON.readTree(noirs.lire("/api/mes-parties").body());
        assertEquals(1, parties.size());
        JsonNode archivee = parties.get(0);
        assertEquals(4, archivee.get("nombreCoups").asInt());
        assertTrue(archivee.get("noirs").get("variation").asInt() > 0);
        assertTrue(archivee.get("blancs").get("variation").asInt() < 0);

        String id = archivee.get("id").asString();
        JsonNode detail = JSON.readTree(blancs.lire("/api/mes-parties/" + id).body());
        assertEquals(4, detail.get("coups").size());
        assertEquals("Dh4", detail.get("coups").get(3).get("notation").asString());
        assertEquals(409, new Joueur("autre").lire("/api/mes-parties/" + id).statusCode());

        JsonNode blitz = null;
        for (JsonNode bilan : JSON.readTree(noirs.lire("/api/mes-bilans").body())) {
            if (bilan.get("categorie").asString().equals("BLITZ")) {
                blitz = bilan;
            }
        }
        assertEquals(1, blitz.get("victoires").asInt());
        assertEquals(2, blitz.get("evolution").size());
        assertTrue(noirs.lire("/api/moi").body().contains("\"BLITZ\":" + archivee.get("noirs").get("classementApres").asInt()));
    }

    private static int nombreDeCoupsLegaux(JsonNode partie) {
        int total = 0;
        for (var arrivees : partie.get("destinations").properties()) {
            total += arrivees.getValue().size();
        }
        return total;
    }

    @Test
    void lesReglesSontAppliquees() throws Exception {
        Joueur[] joueurs = partieCommencee();

        assertEquals(409, joueurs[1].jouer("e7", "e5").statusCode(), "RG3 : pas son trait");
        assertEquals(409, joueurs[0].jouer("e2", "e5").statusCode(), "RG1 : coup illégal");
        assertEquals(409, joueurs[0].jouer("z9", "e4").statusCode(), "case inconnue");
        assertEquals(409, joueurs[0].envoyer("/api/parties", "{\"cadence\":\"5+0\"}").statusCode(), "RG11 : une partie à la fois");
        assertEquals(1, joueurs[1].partie().get("coups").size() + 1);
    }

    @Test
    void laDemandeInvalideEstRefusee() throws Exception {
        Joueur joueur = new Joueur("inva");

        assertEquals(409, joueur.envoyer("/api/parties", "{\"cadence\":\"7+7\"}").statusCode());
        assertEquals(409, joueur.envoyer("/api/parties", "{\"cadence\":\"5+0\",\"couleur\":\"VERT\"}").statusCode());
        assertEquals(409, joueur.envoyer("/api/parties", "{\"cadence\":\"5+0\",\"invite\":\"personne\"}").statusCode());
        assertEquals(409, joueur.envoyer("/api/parties/ordinateur", "{\"elo\":50}").statusCode());
        assertEquals(409, joueur.envoyer("/api/parties/ordinateur", "{\"elo\":3191}").statusCode());
        assertEquals(409, joueur.envoyer("/api/parties/" + UUID.randomUUID() + "/rejoindre", "{}").statusCode());
        assertEquals(204, joueur.lire("/api/partie").statusCode());
    }

    @Test
    void uneInvitationEstVueSeulementParLInvite() throws Exception {
        Joueur createur = new Joueur("hote");
        Joueur invite = new Joueur("invi");
        Joueur autre = new Joueur("tier");
        assertEquals(201, createur.envoyer("/api/parties", "{\"cadence\":\"3+2\",\"invite\":\"" + invite.pseudo + "\"}").statusCode());

        JsonNode salonInvite = JSON.readTree(invite.lire("/api/parties").body());
        assertEquals(1, salonInvite.get("invitations").size());
        assertFalse(autre.lire("/api/parties").body().contains(createur.pseudo));

        assertEquals(204, invite.envoyer("/api/parties/" + salonInvite.get("invitations").get(0).get("id").asString() + "/refus", "{}").statusCode());
        assertEquals("annulee", createur.partie().get("issue").asString());
    }

    @Test
    void leCoupDeLAdversaireArriveParLeWebSocket() throws Exception {
        Joueur[] joueurs = partieCommencee();
        LinkedBlockingQueue<String> recus = new LinkedBlockingQueue<>();
        String cookie = joueurs[1].cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("JSESSIONID")).map(HttpCookie::toString).findFirst().orElseThrow();
        WebSocket ligne = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Cookie", cookie)
                .header("Origin", "http://localhost:" + port)
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/partie"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence texte, boolean dernier) {
                        recus.add(texte.toString());
                        webSocket.request(1);
                        return null;
                    }
                }).get(10, TimeUnit.SECONDS);

        joueurs[0].jouer("e2", "e4");

        String message = recus.poll(10, TimeUnit.SECONDS);
        ligne.abort();
        JsonNode partie = JSON.readTree(message);
        assertEquals("e4", partie.get("coups").get(0).get("notation").asString());
        assertTrue(partie.get("destinations").size() > 0, "c'est maintenant à lui de jouer");
    }

    @Test
    void leWebSocketExigeUneSession() {
        var tentative = HttpClient.newHttpClient().newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/partie"), new WebSocket.Listener() { });

        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> tentative.get(10, TimeUnit.SECONDS));
    }

    @Test
    void laGestionDesComptesEstReserveeAuxAdministrateurs_RG17() throws Exception {
        Joueur joueur = new Joueur("norm");

        assertEquals(403, joueur.lire("/api/admin/comptes").statusCode());
        assertEquals(403, joueur.envoyer("/api/admin/comptes/" + joueur.pseudo + "/suspension", "{}").statusCode());
    }

    @Test
    void leGrandRoqueEstProposeEtDeplaceLaTour_RG2() throws Exception {
        Joueur[] joueurs = partieCommencee();
        Joueur blancs = joueurs[0];
        Joueur noirs = joueurs[1];
        String[][] coups = {{"d2", "d4"}, {"a7", "a6"}, {"c1", "f4"}, {"a6", "a5"}, {"b1", "c3"}, {"h7", "h6"}, {"d1", "d2"}, {"h6", "h5"}};
        for (int indice = 0; indice < coups.length; indice++) {
            assertEquals(200, (indice % 2 == 0 ? blancs : noirs).jouer(coups[indice][0], coups[indice][1]).statusCode());
        }

        String destinationsDuRoi = blancs.partie().get("destinations").get("e1").toString();
        assertTrue(destinationsDuRoi.contains("c1"), destinationsDuRoi);

        assertEquals(200, blancs.jouer("e1", "c1").statusCode());
        JsonNode apres = blancs.partie();
        assertEquals("O-O-O", apres.get("coups").get(8).get("notation").asString());
        assertTrue(apres.get("position").asString().startsWith("rnbqkbnr/1pppppp1/8/p6p/3P1B2/2N5/PPPQPPPP/2KR1BNR"), apres.get("position").asString());
    }

    @Test
    void laRechercheProposeDesJoueursSansSeProposerSoiMeme() throws Exception {
        Joueur cherche = new Joueur("zoe");
        Joueur moi = new Joueur("zoltan");

        JsonNode suggestions = JSON.readTree(moi.lire("/api/joueurs?debut=ZO").body());

        assertTrue(suggestions.toString().contains(cherche.pseudo));
        assertFalse(suggestions.toString().contains(moi.pseudo));
        assertEquals("[]", moi.lire("/api/joueurs?debut=").body());
        assertEquals(401, HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/joueurs?debut=zo")).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
