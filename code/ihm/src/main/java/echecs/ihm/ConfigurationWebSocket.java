package echecs.ihm;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Only our own pages can open this line: Spring refuses other origins by default. */
@Configuration
@EnableWebSocket
class ConfigurationWebSocket implements WebSocketConfigurer {
    private final DiffusionParties diffusion;

    ConfigurationWebSocket(DiffusionParties diffusion) {
        this.diffusion = diffusion;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registre) {
        registre.addHandler(diffusion, "/ws/partie");
    }
}
