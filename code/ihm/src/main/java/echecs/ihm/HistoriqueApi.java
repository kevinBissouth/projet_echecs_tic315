package echecs.ihm;

import echecs.controle.BilanCadence;
import echecs.controle.ControleurHistorique;
import echecs.controle.PartieArchivee;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The finished games of the connected player, and their results by family of time controls. */
@RestController
class HistoriqueApi {
    private final ControleurHistorique historique;

    HistoriqueApi(ControleurHistorique historique) {
        this.historique = historique;
    }

    @GetMapping("/api/mes-parties")
    List<PartieArchivee> mesParties(Principal connecte) {
        return historique.mesParties(connecte.getName());
    }

    @GetMapping("/api/mes-parties/{id}")
    PartieArchivee partie(@PathVariable UUID id, Principal connecte) {
        return historique.revoir(connecte.getName(), id);
    }

    @GetMapping("/api/mes-bilans")
    List<BilanCadence> bilans(Principal connecte) {
        return historique.mesBilans(connecte.getName());
    }
}
