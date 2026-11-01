package echecs.metier.partie;

import echecs.metier.jeu.Case;
import echecs.metier.jeu.Coup;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.TypePromotion;
import echecs.metier.joueur.CalculClassement;
import echecs.metier.joueur.Classement;
import echecs.metier.joueur.Joueur;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class PartieEntreJoueurs extends Partie {
    private static final double SCORE_VICTOIRE = 1;
    private static final double SCORE_NULLE = 0.5;
    private static final double SCORE_DEFAITE = 0;

    private final Joueur createur;
    private final Joueur invite;
    private final Cadence cadence;
    private final CalculClassement calcul;
    private Couleur nulleProposeePar;

    /**
     * @param couleur the creator's colour, null for a random one when the opponent joins
     * @param invite the only player allowed to join, null for a game open to all
     */
    public PartieEntreJoueurs(Joueur createur, Cadence cadence, Couleur couleur, Joueur invite,
                              CalculClassement calcul, Clock horloge) {
        super(horloge);
        if (createur == invite) {
            throw new IllegalArgumentException("Un joueur ne peut pas s'inviter lui-même");
        }
        this.createur = createur;
        this.invite = invite;
        this.cadence = cadence;
        this.calcul = calcul;
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

    /** Ends the game if the side to move has run out of time; also called by a periodic task. */
    public void verifierTemps() {
        if (etat() == EtatPartie.EN_COURS && participationAuTrait().tempsEcoule(maintenant())) {
            etatCourant().tempsEcoule(this);
            notifierObservateurs();
        }
    }

    @Override
    void demarrer() {
        super.demarrer();
        participationAuTrait().demarrerPendule(maintenant());
    }

    boolean peutEtreRejointePar(Joueur joueur) {
        return joueur != createur && (invite == null || joueur == invite) && joueur.peutJouer();
    }

    boolean estInvite(Joueur joueur) {
        return joueur == invite;
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
