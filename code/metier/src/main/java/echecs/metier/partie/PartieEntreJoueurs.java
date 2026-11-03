package echecs.metier.partie;

import echecs.metier.jeu.Case;
import echecs.metier.jeu.Coup;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.TypePromotion;
import echecs.metier.joueur.CalculClassement;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.Joueur;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class PartieEntreJoueurs extends Partie {
    private static final double SCORE_VICTOIRE = 1;
    private static final double SCORE_NULLE = 0.5;
    private static final double SCORE_DEFAITE = 0;
    /** A game nobody joins does not wait forever. */
    public static final Duration DELAI_D_EXPIRATION = Duration.ofMinutes(3);
    /** The creator has this long to accept a player who asks to join. */
    public static final Duration DELAI_DE_REPONSE = Duration.ofSeconds(30);

    private final Joueur createur;
    private final Joueur invite;
    private final Cadence cadence;
    private final CalculClassement calcul;
    private final Instant creeeLe;
    private Couleur nulleProposeePar;
    private Joueur demandeur;
    private Instant demandeLe;

    /**
     * @param couleur the creator's colour, null for a random one when the opponent joins
     * @param invite the only player allowed to join, null for a game open to all
     */
    public PartieEntreJoueurs(Joueur createur, Cadence cadence, Couleur couleur, Joueur invite,
                              CalculClassement calcul, Clock horloge) {
        super(horloge);
        if (createur.equals(invite)) {
            throw new IllegalArgumentException("Un joueur ne peut pas s'inviter lui-même");
        }
        this.createur = createur;
        this.invite = invite;
        this.cadence = cadence;
        this.calcul = calcul;
        this.creeeLe = maintenant();
        ajouterParticipation(new Participation(createur, couleur, cadence.tempsDeBase()));
    }

    public Joueur createur() {
        return createur;
    }

    public Optional<Joueur> invite() {
        return Optional.ofNullable(invite);
    }

    public Cadence cadence() {
        return cadence;
    }

    public Instant expireLe() {
        return creeeLe.plus(DELAI_D_EXPIRATION);
    }

    public Optional<Joueur> demandeur() {
        return Optional.ofNullable(demandeur);
    }

    public Optional<Instant> demandeExpireLe() {
        return Optional.ofNullable(demandeLe).map(debut -> debut.plus(DELAI_DE_REPONSE));
    }

    public void demanderARejoindre(Joueur joueur) {
        etatCourant().demanderARejoindre(this, joueur);
        notifierObservateurs();
    }

    public void confirmerDemande(Joueur joueur) {
        etatCourant().confirmerDemande(this, joueur);
        notifierObservateurs();
    }

    public void refuserDemande(Joueur joueur) {
        etatCourant().refuserDemande(this, joueur);
        notifierObservateurs();
    }

    public void retirerDemande(Joueur joueur) {
        etatCourant().retirerDemande(this, joueur);
        notifierObservateurs();
    }

    public Optional<Couleur> nulleProposeePar() {
        return Optional.ofNullable(nulleProposeePar);
    }

    public void rejoindre(Joueur joueur) {
        etatCourant().rejoindre(this, joueur);
        notifierObservateurs();
    }

    public void refuserInvitation(Joueur joueur) {
        etatCourant().refuserInvitation(this, joueur);
        notifierObservateurs();
    }

    public void proposerNulle(Joueur joueur) {
        etatCourant().proposerNulle(this, joueur);
        notifierObservateurs();
    }

    public void accepterNulle(Joueur joueur) {
        etatCourant().accepterNulle(this, joueur);
        notifierObservateurs();
    }

    public void refuserNulle(Joueur joueur) {
        etatCourant().refuserNulle(this, joueur);
        notifierObservateurs();
    }

    /** A move made after the flag fell must not count: the time is checked first (RG5). */
    @Override
    public Coup jouerCoup(Joueur joueur, Case depart, Case arrivee, TypePromotion promotion) {
        verifierTemps();
        return super.jouerCoup(joueur, depart, arrivee, promotion);
    }

    /**
     * Called by a periodic task: ends the game if the side to move has run out of time (RG5), cancels a game
     * nobody joined in time, and drops a request its creator did not answer.
     */
    public void verifierTemps() {
        Instant maintenant = maintenant();
        if (etat() == EtatPartie.EN_COURS && participationAuTrait().tempsEcoule(maintenant)) {
            etatCourant().tempsEcoule(this);
            notifierObservateurs();
        } else if (etat() == EtatPartie.EN_ATTENTE && !maintenant.isBefore(expireLe())) {
            annulerParLeSysteme();
        } else if (demandeExpireLe().filter(limite -> !maintenant.isBefore(limite)).isPresent()) {
            effacerDemande();
            notifierObservateurs();
        }
    }

    @Override
    void demarrer() {
        super.demarrer();
        participationAuTrait().demarrerPendule(maintenant());
    }

    /** Both must be free (RG11): the creator may be playing elsewhere while this game waits. */
    boolean peutEtreRejointePar(Joueur joueur) {
        return !joueur.equals(createur) && (invite == null || joueur.equals(invite)) && joueur.peutJouer() && createur.peutJouer();
    }

    void enregistrerDemande(Joueur joueur) {
        demandeur = joueur;
        demandeLe = maintenant();
    }

    void effacerDemande() {
        demandeur = null;
        demandeLe = null;
    }

    boolean estInvite(Joueur joueur) {
        return joueur.equals(invite);
    }

    void ajouterAdversaire(Joueur joueur) {
        ajouterParticipation(new Participation(joueur, null, cadence.tempsDeBase()));
        attribuerCouleurs();
    }

    void enregistrerPropositionNulle(Couleur couleur) {
        nulleProposeePar = couleur;
    }

    boolean aRecuUneProposition(Joueur joueur) {
        return couleurDe(joueur).filter(couleur -> nulleProposeePar == couleur.adverse()).isPresent();
    }

    /** Switches the clocks, and a pending offer from the opponent is refused by playing on (RG4). */
    @Override
    protected void apresCoup() {
        Couleur vientDeJouer = trait().adverse();
        participationDe(vientDeJouer).arreterPendule(maintenant(), cadence.increment());
        participationAuTrait().demarrerPendule(maintenant());
        if (nulleProposeePar == vientDeJouer.adverse()) {
            nulleProposeePar = null;
        }
    }

    @Override
    protected void mettreAJourClassements() {
        Classement blancs = participationDe(Couleur.BLANC).joueur().classement(cadence.categorie());
        Classement noirs = participationDe(Couleur.NOIR).joueur().classement(cadence.categorie());
        double scoreBlancs = scoreBlancs();
        int variationBlancs = calcul.variation(blancs.points(), noirs.points(), scoreBlancs);
        int variationNoirs = calcul.variation(noirs.points(), blancs.points(), SCORE_VICTOIRE - scoreBlancs);
        blancs.modifierPoints(variationBlancs);
        noirs.modifierPoints(variationNoirs);
    }

    private void attribuerCouleurs() {
        Participation premiere = participations().getFirst();
        Couleur couleurCreateur = premiere.couleur().orElseGet(() ->
                ThreadLocalRandom.current().nextBoolean() ? Couleur.BLANC : Couleur.NOIR);
        premiere.definirCouleur(couleurCreateur);
        participations().getLast().definirCouleur(couleurCreateur.adverse());
    }

    private double scoreBlancs() {
        return switch (resultat().orElseThrow()) {
            case VICTOIRE_BLANCS -> SCORE_VICTOIRE;
            case VICTOIRE_NOIRS -> SCORE_DEFAITE;
            case NULLE -> SCORE_NULLE;
        };
    }

    private Participation participationAuTrait() {
        return participationDe(trait());
    }

    private Participation participationDe(Couleur couleur) {
        return participations().stream()
                .filter(participation -> participation.couleur().filter(couleur::equals).isPresent())
                .findFirst()
                .orElseThrow();
    }
}
