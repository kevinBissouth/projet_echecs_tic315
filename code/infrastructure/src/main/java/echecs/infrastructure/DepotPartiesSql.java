package echecs.infrastructure;

import echecs.controle.ArchivesParties;
import echecs.controle.BilanCadence;
import echecs.controle.DepotParties;
import echecs.controle.PartieArchivee;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.MotifFin;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.Participation;
import echecs.metier.partie.Partie;
import echecs.metier.partie.PartieContreOrdinateur;
import echecs.metier.partie.PartieEntreJoueurs;
import echecs.metier.partie.Resultat;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Games being played stay in memory, as decided (a restart loses them); finished games go to PostgreSQL. */
public class DepotPartiesSql implements DepotParties, ArchivesParties {
    private static final Logger JOURNAL = LoggerFactory.getLogger(DepotPartiesSql.class);
    private static final String COLONNES = """
            id, blancs, noirs, force_ordinateur, cadence, categorie, resultat, motif, points_blancs, points_noirs,
            variation_blancs, variation_noirs, position_finale, nombre_coups, terminee_le
            """;

    private final JdbcClient base;
    private final TransactionTemplate transaction;
    private final Set<Partie> enCours = new LinkedHashSet<>();

    public DepotPartiesSql(JdbcClient base, TransactionTemplate transaction) {
        this.base = base;
        this.transaction = transaction;
    }

    @Override
    public void enregistrer(Partie partie) {
        boolean finie = partie.etat() == EtatPartie.TERMINEE || partie.etat() == EtatPartie.ANNULEE;
        synchronized (this) {
            if (finie) {
                enCours.remove(partie);
            } else {
                enCours.add(partie);
            }
        }
        if (partie.etat() == EtatPartie.TERMINEE) {
            archiver(partie);
        }
    }

    @Override
    public synchronized List<PartieEntreJoueurs> partiesOuvertes() {
        return enCours.stream()
                .filter(partie -> partie.etat() == EtatPartie.EN_ATTENTE)
                .filter(PartieEntreJoueurs.class::isInstance)
                .map(PartieEntreJoueurs.class::cast)
                .toList();
    }

    @Override
    public synchronized Optional<Partie> partieActiveDe(Joueur joueur) {
        return enCours.stream()
                .filter(partie -> partie.etat() == EtatPartie.EN_ATTENTE || partie.etat() == EtatPartie.EN_COURS)
                .filter(partie -> partie.participations().stream().map(Participation::joueur).anyMatch(joueur::equals))
                .findFirst();
    }

    /** A storage failure must not break the end of a game being played: it is reported, the players keep their result. */
    private void archiver(Partie partie) {
        try {
            transaction.executeWithoutResult(etat -> {
                if (ecrirePartie(partie) > 0) {
                    ecrireCoups(partie);
                }
            });
        } catch (DataAccessException echec) {
            JOURNAL.error("Partie {} non archivée", partie.id(), echec);
        }
    }

    private int ecrirePartie(Partie partie) {
        CategorieCadence categorie = partie instanceof PartieEntreJoueurs entreJoueurs ? entreJoueurs.cadence().categorie() : null;
        String cadence = partie instanceof PartieEntreJoueurs entreJoueurs ? ecrireCadence(entreJoueurs) : null;
        Integer force = partie instanceof PartieContreOrdinateur contreOrdinateur ? contreOrdinateur.force() : null;
        Joueur blancs = joueurDe(partie, Couleur.BLANC);
        Joueur noirs = joueurDe(partie, Couleur.NOIR);
        Integer pointsBlancs = points(blancs, categorie);
        Integer pointsNoirs = points(noirs, categorie);
        return base.sql("insert into parties (" + COLONNES + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (id) do nothing")
                .params(partie.id(), pseudo(blancs), pseudo(noirs), force, cadence, categorie == null ? null : categorie.name(),
                        partie.resultat().orElseThrow().name(), partie.motifFin().orElseThrow().name(),
                        pointsBlancs, pointsNoirs, variation(blancs, categorie, pointsBlancs), variation(noirs, categorie, pointsNoirs),
                        partie.position(), partie.coups().size(), Timestamp.from(partie.dateFin().orElseThrow()))
                .update();
    }

    private void ecrireCoups(Partie partie) {
        partie.coups().forEach(coup -> base.sql("insert into coups (partie, numero, notation, depart, arrivee, position_apres) values (?, ?, ?, ?, ?, ?)")
                .params(partie.id(), coup.numero(), coup.notation(), coup.depart().nom(), coup.arrivee().nom(), coup.positionApres())
                .update());
    }

    private static String ecrireCadence(PartieEntreJoueurs partie) {
        return partie.cadence().tempsDeBase().toMinutes() + "+" + partie.cadence().increment().toSeconds();
    }

    private static Joueur joueurDe(Partie partie, Couleur couleur) {
        return partie.participations().stream()
                .filter(participation -> participation.couleur().filter(couleur::equals).isPresent())
                .map(Participation::joueur)
                .findFirst().orElse(null);
    }

    private static String pseudo(Joueur joueur) {
        return joueur == null ? null : joueur.pseudo();
    }

    private static Integer points(Joueur joueur, CategorieCadence categorie) {
        return joueur == null || categorie == null ? null : joueur.classement(categorie).points();
    }

    /** The difference with their last stored game in this family: nothing else changes a rating. */
    private Integer variation(Joueur joueur, CategorieCadence categorie, Integer pointsApres) {
        if (pointsApres == null) {
            return null;
        }
        int precedents = base.sql("""
                select case when blancs = :pseudo then points_blancs else points_noirs end
                from parties
                where categorie = :categorie and (blancs = :pseudo or noirs = :pseudo)
                order by terminee_le desc limit 1
                """)
                .param("pseudo", joueur.pseudo())
                .param("categorie", categorie.name())
                .query(Integer.class).optional().orElse(Classement.POINTS_INITIAUX);
        return pointsApres - precedents;
    }

    @Override
    public List<PartieArchivee> dernieresPartiesDe(String pseudo, int nombre) {
        return base.sql("select " + COLONNES + " from parties where blancs = :pseudo or noirs = :pseudo order by terminee_le desc limit :nombre")
                .param("pseudo", pseudo)
                .param("nombre", nombre)
                .query((ligne, rang) -> lirePartie(ligne, List.of()))
                .list();
    }

    @Override
    public Optional<PartieArchivee> trouver(UUID id) {
        List<PartieArchivee.CoupArchive> coups = base.sql("select notation, depart, arrivee, position_apres from coups where partie = ? order by numero")
                .param(id)
                .query((ligne, rang) -> new PartieArchivee.CoupArchive(ligne.getString("notation"), ligne.getString("depart"),
                        ligne.getString("arrivee"), ligne.getString("position_apres")))
                .list();
        return base.sql("select " + COLONNES + " from parties where id = ?")
                .param(id)
                .query((ligne, rang) -> lirePartie(ligne, coups))
                .optional();
    }

    @Override
    public List<BilanCadence> bilansDe(String pseudo) {
        Map<CategorieCadence, Compteur> compteurs = new EnumMap<>(CategorieCadence.class);
        for (CategorieCadence categorie : CategorieCadence.values()) {
            compteurs.put(categorie, new Compteur());
        }
        base.sql("""
                select categorie, points, issue from (
                    select categorie, terminee_le, points_blancs as points,
                           case resultat when 'VICTOIRE_BLANCS' then 'V' when 'NULLE' then 'N' else 'D' end as issue
                    from parties where blancs = :pseudo and categorie is not null
                    union all
                    select categorie, terminee_le, points_noirs,
                           case resultat when 'VICTOIRE_NOIRS' then 'V' when 'NULLE' then 'N' else 'D' end
                    from parties where noirs = :pseudo and categorie is not null
                ) as mes_parties order by terminee_le
                """)
                .param("pseudo", pseudo)
                .query(ligne -> {
                    compteurs.get(CategorieCadence.valueOf(ligne.getString("categorie")))
                            .ajouter(ligne.getInt("points"), ligne.getString("issue"));
                });
        return compteurs.entrySet().stream().map(entree -> entree.getValue().bilan(entree.getKey())).toList();
    }

    private static PartieArchivee lirePartie(ResultSet ligne, List<PartieArchivee.CoupArchive> coups) throws SQLException {
        String categorie = ligne.getString("categorie");
        return new PartieArchivee(ligne.getObject("id", UUID.class),
                new PartieArchivee.Camp(ligne.getString("blancs"), ligne.getObject("points_blancs", Integer.class), ligne.getObject("variation_blancs", Integer.class)),
                new PartieArchivee.Camp(ligne.getString("noirs"), ligne.getObject("points_noirs", Integer.class), ligne.getObject("variation_noirs", Integer.class)),
                ligne.getObject("force_ordinateur", Integer.class), ligne.getString("cadence"),
                categorie == null ? null : CategorieCadence.valueOf(categorie),
                Resultat.valueOf(ligne.getString("resultat")), MotifFin.valueOf(ligne.getString("motif")),
                ligne.getString("position_finale"), ligne.getInt("nombre_coups"), ligne.getTimestamp("terminee_le").toInstant(), coups);
    }

    private static final class Compteur {
        private final List<Integer> evolution = new ArrayList<>(List.of(Classement.POINTS_INITIAUX));
        private int victoires;
        private int nulles;
        private int defaites;

        private void ajouter(int points, String issue) {
            evolution.add(points);
            switch (issue) {
                case "V" -> victoires++;
                case "N" -> nulles++;
                default -> defaites++;
            }
        }

        private BilanCadence bilan(CategorieCadence categorie) {
            int meilleur = evolution.stream().max(Integer::compare).orElse(Classement.POINTS_INITIAUX);
            return new BilanCadence(categorie, meilleur, victoires, nulles, defaites, List.copyOf(evolution));
        }
    }
}
