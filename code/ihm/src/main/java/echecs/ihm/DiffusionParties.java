package echecs.ihm;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

/** The open lines to the players' browsers: the server can speak first, to tell a player their opponent moved. */
@Component
public class DiffusionParties extends TextWebSocketHandler {
    private static final Logger JOURNAL = LoggerFactory.getLogger(DiffusionParties.class);
    private static final int DELAI_ENVOI_MILLISECONDES = 5_000;
    private static final int TAMPON_OCTETS = 256 * 1024;

    private final Map<String, Set<WebSocketSession>> lignes = new ConcurrentHashMap<>();
    private final JsonMapper json;

    public DiffusionParties(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession navigateur) {
        // Decorated: the game queues of two players may write to the same line at the same moment.
        lignes.computeIfAbsent(navigateur.getPrincipal().getName(), pseudo -> ConcurrentHashMap.newKeySet())
                .add(new ConcurrentWebSocketSessionDecorator(navigateur, DELAI_ENVOI_MILLISECONDES, TAMPON_OCTETS));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession navigateur, CloseStatus statut) {
        Set<WebSocketSession> sessions = lignes.get(navigateur.getPrincipal().getName());
        if (sessions != null) {
            sessions.removeIf(session -> session.getId().equals(navigateur.getId()));
        }
    }

    /** A browser that left must not disturb the game: the failure is reported and the game goes on. */
    public void envoyer(String pseudo, Object contenu) {
        Set<WebSocketSession> sessions = lignes.get(pseudo);
        if (sessions == null) {
            return;
        }
        TextMessage message = new TextMessage(json.writeValueAsString(contenu));
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(message);
            } catch (IOException | RuntimeException echec) {
                JOURNAL.warn("Envoi impossible vers {} : {}", pseudo, echec.getMessage());
            }
        }
    }
}
