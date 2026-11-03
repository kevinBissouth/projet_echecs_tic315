package echecs.ihm;

import echecs.controle.ControleurPartie;
import echecs.controle.DepotJoueurs;
import echecs.controle.DepotParties;
import echecs.controle.FilesDeParties;
import echecs.controle.MoteurEchecs;
import echecs.metier.joueur.CalculClassement;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.ObservateurPartie;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** One game controller per player, kept across requests: it remembers the game they are in. */
@Component
public class ControleursDeJoueurs {
    private static final Logger JOURNAL = LoggerFactory.getLogger(ControleursDeJoueurs.class);
    private static final long INTERVALLE_DES_PENDULES_MILLISECONDES = 1_000;

    private final Map<String, ControleurPartie> controleurs = new ConcurrentHashMap<>();
    private final MoteurEchecs moteur;
    private final CalculClassement calcul;
    private final DepotParties depotParties;
    private final DepotJoueurs depotJoueurs;
    private final FilesDeParties files;
    private final ExecutorService calculsDuMoteur;
    private final Clock horloge;
    private final PresentationPartie presentation;
    private final DiffusionParties diffusion;

    public ControleursDeJoueurs(MoteurEchecs moteur, CalculClassement calcul, DepotParties depotParties, DepotJoueurs depotJoueurs,
                                FilesDeParties files, ExecutorService calculsDuMoteur, Clock horloge,
                                PresentationPartie presentation, DiffusionParties diffusion) {
        this.moteur = moteur;
        this.calcul = calcul;
        this.depotParties = depotParties;
        this.depotJoueurs = depotJoueurs;
        this.files = files;
        this.calculsDuMoteur = calculsDuMoteur;
        this.horloge = horloge;
        this.presentation = presentation;
        this.diffusion = diffusion;
    }

    public ControleurPartie de(Joueur joueur) {
        return controleurs.computeIfAbsent(joueur.pseudo(), pseudo -> new ControleurPartie(joueur, vue(joueur), moteur, calcul,
                depotParties, depotJoueurs, files, calculsDuMoteur, horloge));
    }

    /** Observer pattern: the game tells this player's screen that it changed, without knowing what a screen is. */
    private ObservateurPartie vue(Joueur joueur) {
        return partie -> diffusion.envoyer(joueur.pseudo(), presentation.pour(partie, joueur));
    }

    /** A flag must fall even if nobody plays anymore (RG5). */
    @Scheduled(fixedDelay = INTERVALLE_DES_PENDULES_MILLISECONDES)
    void verifierLesPendules() {
        try {
            files.verifierToutesLesPendules();
        } catch (RuntimeException echec) {
            JOURNAL.error("Vérification des pendules interrompue", echec);
        }
    }
}
