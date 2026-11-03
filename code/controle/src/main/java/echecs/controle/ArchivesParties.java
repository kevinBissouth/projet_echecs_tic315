package echecs.controle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The finished games, kept for good (BF11). */
public interface ArchivesParties {
    /** @return their latest games, most recent first, without the moves */
    List<PartieArchivee> dernieresPartiesDe(String pseudo, int nombre);

    Optional<PartieArchivee> trouver(UUID id);

    List<BilanCadence> bilansDe(String pseudo);
}
