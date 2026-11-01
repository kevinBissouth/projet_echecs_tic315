package echecs.controle;

/** This action is reserved for an administrator (RG17). */
public class AccesRefuseException extends RuntimeException {
    public AccesRefuseException(String motif) {
        super(motif);
    }
}
