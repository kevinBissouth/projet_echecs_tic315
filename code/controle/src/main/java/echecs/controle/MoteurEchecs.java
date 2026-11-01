package echecs.controle;

/** The chess engine, seen from the application; an adapter translates this into UCI. */
public interface MoteurEchecs {
    /**
     * @param position the position in FEN
     * @return the move in UCI notation, such as "e2e4" or "e7e8q"
     * @throws MoteurIndisponibleException if the engine does not answer in time
     */
    String calculerCoup(String position, int force);
}
