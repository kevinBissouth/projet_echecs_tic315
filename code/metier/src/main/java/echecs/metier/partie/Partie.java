package echecs.metier.partie;

import echecs.metier.jeu.Case;
import echecs.metier.jeu.Coup;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.MotifFin;
import echecs.metier.jeu.Piece;
import echecs.metier.jeu.Plateau;
import echecs.metier.jeu.TypePromotion;
import echecs.metier.joueur.Joueur;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class Partie {
    private static final int OCCURRENCES_REPETITION = 3;
    // The 50-move rule counts moves of both sides: 100 half-moves.
    private static final int DEMI_COUPS_REGLE_DES_CINQUANTE = 100;
    private static final int DEMI_COUPS_PAR_COUP = 2;

    private final UUID id = UUID.randomUUID();
    private final Clock horloge;
    private final Plateau plateau = new Plateau();
    private final String positionInitiale = plateau.position(Couleur.BLANC);
    private final List<Coup> coups = new ArrayList<>();
    private final List<Participation> participations = new ArrayList<>();
    private final List<ObservateurPartie> observateurs = new ArrayList<>();
    private EtatDePartie etatCourant = new EtatEnAttente();
    private Instant dateDebut;
    private Instant dateFin;
    private Resultat resultat;
    private MotifFin motifFin;

    protected Partie(Clock horloge) {
        this.horloge = horloge;
    }

    public UUID id() {
        return id;
    }

    public EtatPartie etat() {
        return etatCourant.valeur();
    }

    public Couleur trait() {
        return coups.size() % DEMI_COUPS_PAR_COUP == 0 ? Couleur.BLANC : Couleur.NOIR;
    }

    /** The full FEN text of the position, as an engine expects it. */
    public String position() {
        int coupComplet = coups.size() / DEMI_COUPS_PAR_COUP + 1;
        return plateau.position(trait()) + " " + demiCoupsSansPriseNiPion() + " " + coupComplet;
    }

    public Plateau plateau() {
        return plateau;
    }

    public List<Coup> coups() {
        return Collections.unmodifiableList(coups);
    }

    public List<Participation> participations() {
        return Collections.unmodifiableList(participations);
    }

    public Optional<Instant> dateDebut() {
        return Optional.ofNullable(dateDebut);
    }

    public Optional<Instant> dateFin() {
        return Optional.ofNullable(dateFin);
    }

    public Optional<Resultat> resultat() {
        return Optional.ofNullable(resultat);
    }

    public Optional<MotifFin> motifFin() {
        return Optional.ofNullable(motifFin);
    }

    public Coup jouerCoup(Joueur joueur, Case depart, Case arrivee, TypePromotion promotion) {
        Coup coup = etatCourant.jouerCoup(this, joueur, depart, arrivee, promotion);
        notifierObservateurs();
        return coup;
    }

    public void annuler(Joueur joueur) {
        etatCourant.annuler(this, joueur);
        notifierObservateurs();
    }

    /** The system cancels: the engine no longer answers, or the waiting creator was suspended (RG8, RG15). */
    public void annulerParLeSysteme() {
        etatCourant.annulerParLeSysteme(this);
        notifierObservateurs();
    }

    public void abandonner(Joueur joueur) {
        etatCourant.abandonner(this, joueur);
        notifierObservateurs();
    }

    public void abonner(ObservateurPartie observateur) {
        observateurs.add(observateur);
    }

    public void desabonner(ObservateurPartie observateur) {
        observateurs.remove(observateur);
    }

    final EtatDePartie etatCourant() {
        return etatCourant;
    }

    final void changerEtat(EtatDePartie etat) {
        etatCourant = etat;
    }

    /** Overridden when a game starts something more, such as the clocks. */
    /** RG11: a player is busy from the moment a game starts, not while it waits for an opponent. */
    void demarrer() {
        participations.forEach(participation -> participation.joueur().occuper());
        dateDebut = maintenant();
        changerEtat(new EtatEnCours());
    }

    final Coup executerCoup(Joueur joueur, Case depart, Case arrivee, TypePromotion promotion) {
        if (joueur != null) {
            verifierTrait(joueur);
        }
        Piece piece = depart.piece()
                .filter(candidate -> candidate.couleur() == trait())
                .orElseThrow(() -> new CoupRefuseException("Aucune pièce du camp qui a le trait en " + depart));
        Coup coup = enregistrerCoup(piece, depart, arrivee, promotion);
        apresCoup();
        return coup;
    }

    final void terminer(Resultat resultatFinal, MotifFin motif) {
        resultat = resultatFinal;
        motifFin = motif;
        clore(new EtatTerminee());
        mettreAJourClassements();
    }

    final void cloreSansResultat() {
        clore(new EtatAnnulee());
    }

    final int nombreCoupsJoues() {
        return coups.size();
    }

    final Optional<MotifFin> motifDeFin() {
        Optional<MotifFin> situation = plateau.situationFinale(trait());
        return situation.isPresent() ? situation : verifierNullesAutomatiques();
    }

    /** A mate is won by the side that just played; every other end of a move is a draw (RG4, RG9). */
    final Resultat resultatDe(MotifFin motif) {
        return motif == MotifFin.MAT ? Resultat.victoireDe(trait().adverse()) : Resultat.NULLE;
    }

    final boolean estParticipant(Joueur joueur) {
        return participationDe(joueur).isPresent();
    }

    final Optional<Couleur> couleurDe(Joueur joueur) {
        return participationDe(joueur).flatMap(Participation::couleur);
    }

    final void ajouterParticipation(Participation participation) {
        participations.add(participation);
    }

    final Instant maintenant() {
        return horloge.instant();
    }

    final void notifierObservateurs() {
        List.copyOf(observateurs).forEach(observateur -> observateur.actualiser(this));
    }

    /** What a game does after a move, beyond recording it: the clocks of a game between players. */
    protected void apresCoup() {
    }

    /** Only a game between players is rated (RG6). */
    protected void mettreAJourClassements() {
    }

    private void verifierTrait(Joueur joueur) {
        Couleur couleur = couleurDe(joueur)
                .orElseThrow(() -> new CoupRefuseException(joueur.pseudo() + " ne participe pas à cette partie"));
        if (couleur != trait()) {
            throw new CoupRefuseException("Ce n'est pas le tour de " + joueur.pseudo());
        }
    }

    private Coup enregistrerCoup(Piece piece, Case depart, Case arrivee, TypePromotion promotion) {
        Optional<Piece> piecePrise;
        try {
            piecePrise = plateau.deplacer(depart, arrivee, promotion);
        } catch (IllegalArgumentException coupIllegal) {
            throw new CoupRefuseException(coupIllegal.getMessage());
        }
        Piece piecePromue = promotion == null ? null : arrivee.piece().orElseThrow();
        Coup coup = new Coup(coups.size() + 1, depart, arrivee, piece, piecePrise.orElse(null), piecePromue,
                plateau.position(trait().adverse()));
        coups.add(coup);
        return coup;
    }

    private Optional<MotifFin> verifierNullesAutomatiques() {
        String derniere = coups.getLast().positionApres();
        long occurrences = coups.stream().filter(coup -> coup.positionApres().equals(derniere)).count()
                + (positionInitiale.equals(derniere) ? 1 : 0);
        if (occurrences >= OCCURRENCES_REPETITION) {
            return Optional.of(MotifFin.TRIPLE_REPETITION);
        }
        return demiCoupsSansPriseNiPion() >= DEMI_COUPS_REGLE_DES_CINQUANTE
                ? Optional.of(MotifFin.CINQUANTE_COUPS)
                : Optional.empty();
    }

    private int demiCoupsSansPriseNiPion() {
        int demiCoups = 0;
        for (int indice = coups.size() - 1; indice >= 0 && !coups.get(indice).estUnePriseOuUnCoupDePion(); indice--) {
            demiCoups++;
        }
        return demiCoups;
    }

    private void clore(EtatDePartie etatFinal) {
        dateFin = maintenant();
        changerEtat(etatFinal);
        // A game that never started never made anyone busy: its players may be playing elsewhere.
        if (dateDebut != null) {
            participations.forEach(participation -> participation.joueur().liberer());
        }
    }

    private Optional<Participation> participationDe(Joueur joueur) {
        return participations.stream().filter(participation -> participation.joueur().equals(joueur)).findFirst();
    }
}
