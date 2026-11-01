package echecs.controle;

public class ConnexionRefuseeException extends RuntimeException {
    public ConnexionRefuseeException(String motif) {
        super(motif);
    }
}
