package echecs.metier.jeu;

import java.util.List;

/** A step on the board, in columns and ranks. */
record Direction(int colonnes, int rangees) {
    static final List<Direction> ORTHOGONALES = List.of(
            new Direction(1, 0), new Direction(-1, 0), new Direction(0, 1), new Direction(0, -1));
    static final List<Direction> DIAGONALES = List.of(
            new Direction(1, 1), new Direction(1, -1), new Direction(-1, 1), new Direction(-1, -1));
    static final List<Direction> TOUTES = List.of(
            new Direction(1, 0), new Direction(-1, 0), new Direction(0, 1), new Direction(0, -1),
            new Direction(1, 1), new Direction(1, -1), new Direction(-1, 1), new Direction(-1, -1));
}
