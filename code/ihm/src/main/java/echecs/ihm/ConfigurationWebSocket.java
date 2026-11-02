package echecs.ihm;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Temporary: an echo, to check that the host keeps WebSocket connections open. */
@Configuration
@EnableWebSocket
class ConfigurationWebSocket implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registre) {
        registre.addHandler(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
                session.sendMessage(new TextMessage("reçu : " + message.getPayload()));
            }
        }, "/ws/essai");
    }
}
