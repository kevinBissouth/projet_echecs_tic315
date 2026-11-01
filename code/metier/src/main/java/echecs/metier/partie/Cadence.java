package echecs.metier.partie;

import echecs.metier.joueur.CategorieCadence;
import java.time.Duration;

public record Cadence(Duration tempsDeBase, Duration increment, CategorieCadence categorie) {
}
