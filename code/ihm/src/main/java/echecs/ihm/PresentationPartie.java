package echecs.ihm;

import echecs.metier.jeu.Case;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.Piece;
import echecs.metier.jeu.Plateau;
import echecs.metier.jeu.Roi;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.Participation;
import echecs.metier.partie.Partie;
import echecs.metier.partie.PartieContreOrdinateur;
import echecs.metier.partie.PartieEntreJoueurs;
import echecs.metier.partie.Resultat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** What one player sees of a game. Must be called from the game's queue: the game must not change meanwhile. */
@Component
public class PresentationPartie {
    private static final String NOM_ORDINATEUR = "Ordinateur";
    private static final String COLONNES = "abcdefgh";
    private static final int RANGEES = 8;
    private static final int COUPS_AVANT_FIN_DE_L_ANNULATION = 2;

    public record VueCamp(String pseudo, Integer classement, Long tempsMillisecondes, boolean penduleActive) { }

    public record VueCoup(String notation, String depart, String arrivee) { }

    public record VuePartie(String id, boolean contreOrdinateur, String etat, String position, String trait, String maCouleur,
                            VueCamp moi, VueCamp adversaire, String cadence, CategorieCadence categorie, List<VueCoup> coups,
                            Map<String, List<String>> destinations, String caseEnEchec, boolean nulleProposeeParMoi,
                            boolean nulleProposeeParAdversaire, boolean annulable, String issue, String motif,
                            boolean maDemande, VueCamp demande, Long delaiDeReponseMillisecondes, Long expirationMillisecondes) { }

    private final Clock horloge;

    public PresentationPartie(Clock horloge) {
        this.horloge = horloge;
    }

    public VuePartie pour(Partie partie, Joueur joueur) {
        Optional<Couleur> maCouleur = participationDe(partie, joueur).flatMap(Participation::couleur);
        boolean contreOrdinateur = partie instanceof PartieContreOrdinateur;
        CategorieCadence categorie = partie instanceof PartieEntreJoueurs entreJoueurs ? entreJoueurs.cadence().categorie() : null;
        String cadence = partie instanceof PartieEntreJoueurs entreJoueurs
                ? entreJoueurs.cadence().tempsDeBase().toMinutes() + "+" + entreJoueurs.cadence().increment().toSeconds()
                : null;
        return new VuePartie(partie.id().toString(), contreOrdinateur, partie.etat().name(), partie.position().split(" ")[0],
                partie.trait().name(), maCouleur.map(Enum::name).orElse(null),
                campDe(partie, participationDe(partie, joueur), categorie),
                adversaire(partie, joueur, categorie),
                cadence, categorie,
                partie.coups().stream().map(coup -> new VueCoup(coup.notation(), coup.depart().nom(), coup.arrivee().nom())).toList(),
                destinations(partie, maCouleur),
                caseEnEchec(partie),
                nulleProposee(partie, maCouleur, true),
                nulleProposee(partie, maCouleur, false),
                partie.etat() == EtatPartie.EN_ATTENTE
                        || (partie.etat() == EtatPartie.EN_COURS && partie.coups().size() < COUPS_AVANT_FIN_DE_L_ANNULATION),
                issue(partie, maCouleur),
                partie.motifFin().map(Enum::name).orElse(null),
                demandeEnAttente(partie).filter(joueur::equals).isPresent(),
                demandeVueParLeCreateur(partie, joueur, categorie),
                demandeEnAttente(partie).isEmpty() ? null : restantAvant(((PartieEntreJoueurs) partie).demandeExpireLe().orElseThrow()),
                partie instanceof PartieEntreJoueurs entreJoueurs && partie.etat() == EtatPartie.EN_ATTENTE
                        ? restantAvant(entreJoueurs.expireLe()) : null);
    }

    /** Durations rather than dates: the phone's clock may differ from the server's, a duration stays right on both. */
    public long restantAvant(Instant echeance) {
        return Math.max(Duration.between(horloge.instant(), echeance).toMillis(), 0);
    }

    private static Optional<Joueur> demandeEnAttente(Partie partie) {
        return partie instanceof PartieEntreJoueurs entreJoueurs && partie.etat() == EtatPartie.EN_ATTENTE
                ? entreJoueurs.demandeur() : Optional.empty();
    }

    /** Only the creator is asked to accept: the one who asked sees that they wait. */
    private static VueCamp demandeVueParLeCreateur(Partie partie, Joueur joueur, CategorieCadence categorie) {
        if (!(partie instanceof PartieEntreJoueurs entreJoueurs) || !entreJoueurs.createur().equals(joueur)) {
            return null;
        }
        return demandeEnAttente(partie)
                .map(demandeur -> new VueCamp(demandeur.pseudo(), demandeur.classement(categorie).points(), null, false))
                .orElse(null);
    }

    private VueCamp adversaire(Partie partie, Joueur joueur, CategorieCadence categorie) {
        if (partie instanceof PartieContreOrdinateur contreOrdinateur) {
            return new VueCamp(NOM_ORDINATEUR, contreOrdinateur.force(), null, false);
        }
        return campDe(partie, partie.participations().stream().filter(participation -> !participation.joueur().equals(joueur)).findFirst(), categorie);
    }

    private VueCamp campDe(Partie partie, Optional<Participation> participation, CategorieCadence categorie) {
        if (participation.isEmpty()) {
            return null;
        }
        Participation camp = participation.get();
        Instant maintenant = horloge.instant();
        Long temps = camp.tempsRestantA(maintenant).map(Duration::toMillis).map(reste -> Math.max(reste, 0)).orElse(null);
        Integer classement = categorie == null ? null : camp.joueur().classement(categorie).points();
        boolean active = partie.etat() == EtatPartie.EN_COURS && temps != null && camp.couleur().filter(partie.trait()::equals).isPresent();
        return new VueCamp(camp.joueur().pseudo(), classement, temps, active);
    }

    /** The server knows the rules: the board only offers what it is told is legal. */
    private static Map<String, List<String>> destinations(Partie partie, Optional<Couleur> maCouleur) {
        Map<String, List<String>> permises = new LinkedHashMap<>();
        if (partie.etat() != EtatPartie.EN_COURS || maCouleur.filter(partie.trait()::equals).isEmpty()) {
            return permises;
        }
        Plateau plateau = partie.plateau();
        toutesLesCases(plateau).stream()
                .filter(depart -> depart.piece().map(Piece::couleur).filter(maCouleur.get()::equals).isPresent())
                .forEach(depart -> {
                    List<String> arrivees = plateau.destinationsLegales(depart).stream().map(Case::nom).toList();
                    if (!arrivees.isEmpty()) {
                        permises.put(depart.nom(), arrivees);
                    }
                });
        return permises;
    }

    private static String caseEnEchec(Partie partie) {
        Plateau plateau = partie.plateau();
        Couleur trait = partie.trait();
        if (!plateau.enEchec(trait)) {
            return null;
        }
        return toutesLesCases(plateau).stream()
                .filter(position -> position.piece().filter(piece -> piece instanceof Roi && piece.couleur() == trait).isPresent())
                .map(Case::nom)
                .findFirst().orElse(null);
    }

    private static List<Case> toutesLesCases(Plateau plateau) {
        return COLONNES.chars().boxed()
                .flatMap(colonne -> java.util.stream.IntStream.rangeClosed(1, RANGEES)
                        .mapToObj(rangee -> plateau.caseEn(String.valueOf((char) colonne.intValue()), rangee)))
                .toList();
    }

    private static boolean nulleProposee(Partie partie, Optional<Couleur> maCouleur, boolean parMoi) {
        if (!(partie instanceof PartieEntreJoueurs entreJoueurs) || maCouleur.isEmpty()) {
            return false;
        }
        return entreJoueurs.nulleProposeePar().filter(couleur -> (couleur == maCouleur.get()) == parMoi).isPresent();
    }

    private static String issue(Partie partie, Optional<Couleur> maCouleur) {
        if (partie.etat() == EtatPartie.ANNULEE) {
            return "annulee";
        }
        return partie.resultat().map(resultat -> {
            if (resultat == Resultat.NULLE) {
                return "nulle";
            }
            Couleur gagnant = resultat == Resultat.VICTOIRE_BLANCS ? Couleur.BLANC : Couleur.NOIR;
            return maCouleur.filter(gagnant::equals).isPresent() ? "gagnee" : "perdue";
        }).orElse(null);
    }

    private static Optional<Participation> participationDe(Partie partie, Joueur joueur) {
        return partie.participations().stream().filter(participation -> participation.joueur().equals(joueur)).findFirst();
    }
}
