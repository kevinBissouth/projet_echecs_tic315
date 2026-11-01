package echecs.controle;

/** The external identity provider; an adapter translates this into OpenID Connect. */
public interface ServiceAuthentification {
    /**
     * @return the verified e-mail address of the player
     * @throws ConnexionRefuseeException if the token is not valid
     */
    String verifierIdentite(String jeton);
}
