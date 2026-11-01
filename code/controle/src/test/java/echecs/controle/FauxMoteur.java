package echecs.controle;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/** Answers the prepared moves in order, and is silent once it has no more of them. */
final class FauxMoteur implements MoteurEchecs {
    private final Queue<String> reponses;
    int demandes = 0;
    String dernierePosition;

    FauxMoteur(String... reponses) {
        this.reponses = new ArrayDeque<>(List.of(reponses));
    }

    @Override
    public String calculerCoup(String position, int force) {
        demandes++;
        dernierePosition = position;
        String reponse = reponses.poll();
        if (reponse == null) {
            throw new MoteurIndisponibleException("Pas de réponse");
        }
        return reponse;
    }
}
