package echecs.metier.partie;

import echecs.metier.joueur.CategorieCadence;
import java.time.Duration;
import java.util.List;

public record Cadence(Duration tempsDeBase, Duration increment, CategorieCadence categorie) {
    /** The fixed list a game's time control is chosen from (RG5). */
    public static final List<Cadence> DISPONIBLES = List.of(
            cadence(1, 0, CategorieCadence.BULLET),
            cadence(2, 1, CategorieCadence.BULLET),
            cadence(3, 0, CategorieCadence.BLITZ),
            cadence(3, 2, CategorieCadence.BLITZ),
            cadence(5, 0, CategorieCadence.BLITZ),
            cadence(10, 0, CategorieCadence.RAPIDE),
            cadence(10, 5, CategorieCadence.RAPIDE),
            cadence(15, 10, CategorieCadence.RAPIDE));

    private static Cadence cadence(int minutes, int secondesParCoup, CategorieCadence categorie) {
        return new Cadence(Duration.ofMinutes(minutes), Duration.ofSeconds(secondesParCoup), categorie);
    }
}
