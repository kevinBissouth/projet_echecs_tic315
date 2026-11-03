package echecs.ihm;

import java.net.URI;

/** Neon gives postgresql://user:password@host/base addresses; the Java driver expects them in another form. */
final class AdresseDeBase {
    private static final String SCHEMA_ATTENDU = "postgresql";

    private AdresseDeBase() {
    }

    static String versJdbc(String adresse) {
        URI uri = URI.create(adresse.trim());
        if (!SCHEMA_ATTENDU.equals(uri.getScheme()) && !"postgres".equals(uri.getScheme())) {
            throw new IllegalArgumentException("Adresse de base non reconnue");
        }
        String[] identifiants = uri.getRawUserInfo().split(":", 2);
        String requete = "user=" + identifiants[0] + "&password=" + identifiants[1];
        if (uri.getRawQuery() != null) {
            requete += "&" + uri.getRawQuery();
        }
        return "jdbc:postgresql://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + uri.getRawPath() + "?" + requete;
    }
}
