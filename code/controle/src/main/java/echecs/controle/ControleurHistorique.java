package echecs.controle;

import java.util.List;
import java.util.UUID;

/** What a player can consult about their past games. */
public class ControleurHistorique {
    private static final int NOMBRE_MAXIMUM_DE_PARTIES = 50;

    private final ArchivesParties archives;

    public ControleurHistorique(ArchivesParties archives) {
        this.archives = archives;
    }

    public List<PartieArchivee> mesParties(String pseudo) {
        return archives.dernieresPartiesDe(pseudo, NOMBRE_MAXIMUM_DE_PARTIES);
    }

    public List<BilanCadence> mesBilans(String pseudo) {
        return archives.bilansDe(pseudo);
    }

    /** RG7: a finished game can be replayed by its participants only. */
    public PartieArchivee revoir(String pseudo, UUID id) {
        return archives.trouver(id)
                .filter(partie -> partie.aPourParticipant(pseudo))
                .orElseThrow(() -> new ActionRefuseeException("Cette partie ne peut pas être revue"));
    }
}
