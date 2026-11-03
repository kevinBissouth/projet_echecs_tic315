package echecs.metier.partie;

import echecs.metier.jeu.Couleur;
import echecs.metier.joueur.Joueur;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** A player in a game: their colour and, in a game between players, their clock. */
public class Participation {
    private final Joueur joueur;
    private Couleur couleur;
    private Duration tempsRestant;
    private Instant debutTour;

    Participation(Joueur joueur, Couleur couleur, Duration tempsRestant) {
        this.joueur = joueur;
        this.couleur = couleur;
        this.tempsRestant = tempsRestant;
    }

    public Joueur joueur() {
        return joueur;
    }

    public Optional<Couleur> couleur() {
        return Optional.ofNullable(couleur);
    }

    public Optional<Duration> tempsRestant() {
        return Optional.ofNullable(tempsRestant);
    }

    /** What the clock shows now: while this player thinks, the time since the start of their turn is deducted. */
    public Optional<Duration> tempsRestantA(Instant maintenant) {
        if (tempsRestant == null || debutTour == null) {
            return tempsRestant();
        }
        return Optional.of(tempsRestant.minus(Duration.between(debutTour, maintenant)));
    }

    public void definirCouleur(Couleur nouvelleCouleur) {
        couleur = nouvelleCouleur;
    }

    public void demarrerPendule(Instant maintenant) {
        debutTour = maintenant;
    }

    public void arreterPendule(Instant maintenant, Duration increment) {
        tempsRestant = tempsRestant.minus(Duration.between(debutTour, maintenant)).plus(increment);
        debutTour = null;
    }

    public boolean tempsEcoule(Instant maintenant) {
        if (debutTour == null) {
            return false;
        }
        return !tempsRestant.minus(Duration.between(debutTour, maintenant)).isPositive();
    }
}
