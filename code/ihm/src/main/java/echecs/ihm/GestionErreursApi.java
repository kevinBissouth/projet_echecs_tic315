package echecs.ihm;

import echecs.controle.AccesRefuseException;
import echecs.controle.ActionRefuseeException;
import echecs.controle.CompteRefuseException;
import echecs.controle.ConnexionRefuseeException;
import echecs.metier.partie.CoupRefuseException;
import echecs.metier.partie.TransitionInterditeException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** One error format for the whole API. These messages are written for players: they reveal nothing internal. */
@RestControllerAdvice
class GestionErreursApi {
    @ExceptionHandler(CompteRefuseException.class)
    ResponseEntity<Map<String, String>> compteRefuse(CompteRefuseException erreur) {
        return reponse(HttpStatus.CONFLICT, erreur);
    }

    @ExceptionHandler(ConnexionRefuseeException.class)
    ResponseEntity<Map<String, String>> connexionRefusee(ConnexionRefuseeException erreur) {
        return reponse(HttpStatus.UNAUTHORIZED, erreur);
    }

    @ExceptionHandler(AccesRefuseException.class)
    ResponseEntity<Map<String, String>> accesRefuse(AccesRefuseException erreur) {
        return reponse(HttpStatus.FORBIDDEN, erreur);
    }

    /** The action is refused by the rules of the game: not a server error, and the player can be told why. */
    @ExceptionHandler({ActionRefuseeException.class, CoupRefuseException.class, TransitionInterditeException.class})
    ResponseEntity<Map<String, String>> actionRefusee(RuntimeException erreur) {
        return reponse(HttpStatus.CONFLICT, erreur);
    }

    @ExceptionHandler(TropDeTentativesException.class)
    ResponseEntity<Map<String, String>> tropDeTentatives(TropDeTentativesException erreur) {
        return reponse(HttpStatus.TOO_MANY_REQUESTS, erreur);
    }

    private static ResponseEntity<Map<String, String>> reponse(HttpStatus statut, RuntimeException erreur) {
        return ResponseEntity.status(statut).body(Map.of("message", erreur.getMessage()));
    }
}
