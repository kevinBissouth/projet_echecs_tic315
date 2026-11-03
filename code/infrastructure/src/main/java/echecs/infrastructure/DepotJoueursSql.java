package echecs.infrastructure;

import echecs.controle.CompteRefuseException;
import echecs.controle.DepotJoueurs;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.EtatCompte;
import echecs.metier.joueur.Joueur;
import echecs.metier.joueur.RoleCompte;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores accounts in PostgreSQL, and keeps one object per player in memory: their "busy" flag lives only
 * in that object, so two objects for one player would each believe the player is free (RG11).
 */
public class DepotJoueursSql implements DepotJoueurs {
    private static final String LECTURE = """
            select j.pseudo, j.email, j.role, j.etat, c.categorie, c.points
            from joueurs j left join classements c on c.pseudo = j.pseudo
            """;

    private final JdbcClient base;
    private final TransactionTemplate transaction;
    private final Map<String, Joueur> enMemoire = new ConcurrentHashMap<>();

    public DepotJoueursSql(JdbcClient base, TransactionTemplate transaction) {
        this.base = base;
        this.transaction = transaction;
    }

    @Override
    public void creer(Joueur joueur, String empreinteMotDePasse) {
        try {
            transaction.executeWithoutResult(etat -> {
                base.sql("insert into joueurs (pseudo, email, empreinte_mot_de_passe, role, etat) values (?, ?, ?, ?, ?)")
                        .params(joueur.pseudo(), joueur.email(), empreinteMotDePasse, role(joueur).name(), joueur.etat().name())
                        .update();
                ecrireClassements(joueur);
            });
        } catch (DuplicateKeyException doublon) {
            throw new CompteRefuseException("Ce pseudo ou cette adresse e-mail a déjà un compte");
        }
        enMemoire.put(cle(joueur.pseudo()), joueur);
    }

    /** Role, state and points change together: a player must not be stored half updated. */
    @Override
    public void enregistrer(Joueur joueur) {
        transaction.executeWithoutResult(etat -> {
            base.sql("update joueurs set role = ?, etat = ? where pseudo = ?")
                    .params(role(joueur).name(), joueur.etat().name(), joueur.pseudo())
                    .update();
            ecrireClassements(joueur);
        });
    }

    @Override
    public Optional<Joueur> trouverParEmail(String email) {
        return lire(LECTURE + " where j.email = ?", email).stream().findFirst();
    }

    @Override
    public Optional<Joueur> trouverParPseudo(String pseudo) {
        return lire(LECTURE + " where lower(j.pseudo) = lower(?)", pseudo).stream().findFirst();
    }

    @Override
    public Optional<String> empreinteDe(Joueur joueur) {
        return base.sql("select empreinte_mot_de_passe from joueurs where pseudo = ?")
                .param(joueur.pseudo())
                .query(String.class)
                .optional();
    }

    @Override
    public boolean pseudoExiste(String pseudo) {
        return trouverParPseudo(pseudo).isPresent();
    }

    @Override
    public List<Joueur> tous() {
        return lire(LECTURE + " order by j.pseudo");
    }

    private void ecrireClassements(Joueur joueur) {
        for (CategorieCadence categorie : CategorieCadence.values()) {
            base.sql("""
                    insert into classements (pseudo, categorie, points) values (?, ?, ?)
                    on conflict (pseudo, categorie) do update set points = excluded.points
                    """)
                    .params(joueur.pseudo(), categorie.name(), joueur.classement(categorie).points())
                    .update();
        }
    }

    /** One join, not one query per player. A player already in memory stays the same object. */
    private List<Joueur> lire(String requete, Object... parametres) {
        Map<String, Reconstitution> lus = new java.util.LinkedHashMap<>();
        base.sql(requete).params(parametres).query(ligne -> {
            Reconstitution joueur = lus.computeIfAbsent(ligne.getString("pseudo"), pseudo -> new Reconstitution(pseudo));
            joueur.remplir(ligne.getString("email"), ligne.getString("role"), ligne.getString("etat"));
            String categorie = ligne.getString("categorie");
            if (categorie != null) {
                joueur.points.put(CategorieCadence.valueOf(categorie), ligne.getInt("points"));
            }
        });
        return lus.values().stream()
                .map(lu -> enMemoire.computeIfAbsent(cle(lu.pseudo), cle -> lu.joueur()))
                .toList();
    }

    private static String cle(String pseudo) {
        return pseudo.toLowerCase(Locale.ROOT);
    }

    private static RoleCompte role(Joueur joueur) {
        return joueur.estAdministrateur() ? RoleCompte.ADMINISTRATEUR : RoleCompte.JOUEUR;
    }

    private static final class Reconstitution {
        private final String pseudo;
        private final Map<CategorieCadence, Integer> points = new EnumMap<>(CategorieCadence.class);
        private String email;
        private String role;
        private String etat;

        private Reconstitution(String pseudo) {
            this.pseudo = pseudo;
        }

        private void remplir(String emailLu, String roleLu, String etatLu) {
            email = emailLu;
            role = roleLu;
            etat = etatLu;
        }

        private Joueur joueur() {
            return Joueur.reconstituer(pseudo, email, RoleCompte.valueOf(role), EtatCompte.valueOf(etat), points);
        }
    }
}
