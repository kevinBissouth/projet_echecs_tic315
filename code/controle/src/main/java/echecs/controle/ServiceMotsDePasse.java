package echecs.controle;

/** Passwords are never stored: only a fingerprint, from which the password cannot be recovered. */
public interface ServiceMotsDePasse {
    String hacher(String motDePasse);

    boolean correspond(String motDePasse, String empreinte);
}
