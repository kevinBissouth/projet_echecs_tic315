package echecs.controle;

/** The account cannot be created: the pseudo or the e-mail is already taken (RG12). */
public class CompteRefuseException extends RuntimeException {
    public CompteRefuseException(String motif) {
        super(motif);
    }
}
