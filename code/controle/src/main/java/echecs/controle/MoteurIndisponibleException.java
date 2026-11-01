package echecs.controle;

public class MoteurIndisponibleException extends RuntimeException {
    public MoteurIndisponibleException(String motif) {
        super(motif);
    }
}
