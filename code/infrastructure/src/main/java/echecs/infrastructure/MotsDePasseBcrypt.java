package echecs.infrastructure;

import echecs.controle.ServiceMotsDePasse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** BCrypt adds a random salt and is slow on purpose: stolen fingerprints stay costly to attack. */
public class MotsDePasseBcrypt implements ServiceMotsDePasse {
    private final BCryptPasswordEncoder encodeur = new BCryptPasswordEncoder();

    @Override
    public String hacher(String motDePasse) {
        return encodeur.encode(motDePasse);
    }

    @Override
    public boolean correspond(String motDePasse, String empreinte) {
        return encodeur.matches(motDePasse, empreinte);
    }
}
