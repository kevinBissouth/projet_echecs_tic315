package echecs.controle;

import echecs.metier.jeu.MotifFin;
import echecs.metier.joueur.CategorieCadence;
import echecs.metier.partie.Resultat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A finished game as it was stored. It is only read: a finished game no longer changes, so it needs none of
 * the rules of a game being played. A side without a player is the computer.
 */
public record PartieArchivee(UUID id, Camp blancs, Camp noirs, Integer forceOrdinateur, String cadence, CategorieCadence categorie,
                             Resultat resultat, MotifFin motif, String positionFinale, int nombreCoups, Instant termineeLe,
                             List<CoupArchive> coups) {
    public record Camp(String pseudo, Integer classementApres, Integer variation) { }

    public record CoupArchive(String notation, String depart, String arrivee, String positionApres) { }

    public boolean aPourParticipant(String pseudo) {
        return pseudo.equals(blancs.pseudo()) || pseudo.equals(noirs.pseudo());
    }
}
