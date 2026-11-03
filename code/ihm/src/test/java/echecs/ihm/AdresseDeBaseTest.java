package echecs.ihm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AdresseDeBaseTest {
    @Test
    void lesIdentifiantsPassentDansLesParametres() {
        assertEquals("jdbc:postgresql://hote.exemple/base?user=moi&password=se%2Fcret&sslmode=require",
                AdresseDeBase.versJdbc("postgresql://moi:se%2Fcret@hote.exemple/base?sslmode=require"));
    }

    @Test
    void uneAdresseSansParametresEtAvecPortEstAcceptee() {
        assertEquals("jdbc:postgresql://hote.exemple:5433/base?user=moi&password=secret",
                AdresseDeBase.versJdbc("postgres://moi:secret@hote.exemple:5433/base"));
    }

    @Test
    void uneAutreAdresseEstRefusee() {
        assertThrows(IllegalArgumentException.class, () -> AdresseDeBase.versJdbc("mysql://moi:secret@hote.exemple/base"));
    }
}
