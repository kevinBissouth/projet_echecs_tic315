package echecs.ihm;

import echecs.controle.MoteurEchecs;
import echecs.infrastructure.AdaptateurStockfish;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
    private static final Duration TEMPS_DE_REFLEXION = Duration.ofSeconds(1);
    private static final Duration DELAI_DE_REPONSE = Duration.ofSeconds(15);

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    MoteurEchecs moteurEchecs(@Value("${echecs.stockfish.executable}") String executable) {
        return new AdaptateurStockfish(executable, TEMPS_DE_REFLEXION, DELAI_DE_REPONSE);
    }
}
