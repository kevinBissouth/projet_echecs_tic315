package echecs.ihm;

import java.io.Serializable;

/** A first visit through Google, kept in the session until the player has chosen a pseudo and a password. */
record IdentiteGoogle(String jeton, String nom, String email) implements Serializable {
    static final String ATTRIBUT_DE_SESSION = "echecs.identiteGoogle";
}
