package echecs.controle;

import echecs.metier.jeu.Case;
import echecs.metier.jeu.Coup;
import echecs.metier.jeu.Couleur;
import echecs.metier.jeu.Plateau;
import echecs.metier.jeu.TypePromotion;
import echecs.metier.joueur.CalculClassement;
import echecs.metier.joueur.Joueur;
import echecs.metier.partie.Cadence;
import echecs.metier.partie.CoupRefuseException;
import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.ObservateurPartie;
import echecs.metier.partie.Partie;
import echecs.metier.partie.PartieContreOrdinateur;
import echecs.metier.partie.PartieEntreJoueurs;
import echecs.metier.partie.TransitionInterditeException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

/** GRASP Controller: one per connected player. It receives actions and delegates, without playing chess. */
public class ControleurPartie implements IPartie {
    private static final Pattern NOM_DE_CASE = Pattern.compile("[a-h][1-8]");
    private static final int FIN_DEPART_UCI = 2;
    private static final int FIN_ARRIVEE_UCI = 4;
    private static final Map<Character, TypePromotion> PROMOTIONS_UCI = Map.of(
            'q', TypePromotion.DAME, 'r', TypePromotion.TOUR, 'b', TypePromotion.FOU, 'n', TypePromotion.CAVALIER);
    // UC7: the request is renewed once before the game is cancelled (E1, RG15).
    private static final int TENTATIVES_DU_MOTEUR = 2;

    private final Joueur joueurConnecte;
    private final ObservateurPartie vue;
    private final MoteurEchecs moteur;
    private final CalculClassement calcul;
    private final DepotParties depotParties;
    private final FilesDeParties files;
    private final ObservateurPartie archivage;
    private final Executor tachesDeFond;
    private final Clock horloge;
    // Read by the web server's threads: each may serve a different request of this player.
    private volatile Partie partieCourante;

    /** @param tachesDeFond where the engine thinks: never in the game's queue, which it would block */
    public ControleurPartie(Joueur joueurConnecte, ObservateurPartie vue, MoteurEchecs moteur, CalculClassement calcul,
                            DepotParties depotParties, DepotJoueurs depotJoueurs, FilesDeParties files,
                            Executor tachesDeFond, Clock horloge) {
        this.joueurConnecte = joueurConnecte;
        this.vue = vue;
        this.moteur = moteur;
        this.calcul = calcul;
        this.depotParties = depotParties;
        this.files = files;
        this.archivage = new ArchivagePartie(depotParties, depotJoueurs, files);
        this.tachesDeFond = tachesDeFond;
        this.horloge = horloge;
    }

    @Override
    public PartieEntreJoueurs creerPartie(Cadence cadence, Couleur couleur, Joueur invite) {
        if (!Cadence.DISPONIBLES.contains(cadence)) {
            throw new ActionRefuseeException("Cette cadence n'existe pas");
        }
        exigerDePouvoirJouer();
        PartieEntreJoueurs partie = new PartieEntreJoueurs(joueurConnecte, cadence, couleur, invite, calcul, horloge);
        suivre(partie);
        return partie;
    }

    @Override
    public List<PartieEntreJoueurs> consulterPartiesOuvertes() {
        return depotParties.partiesOuvertes();
    }

    @Override
    public void rejoindrePartie(PartieEntreJoueurs partie) {
        files.executer(partie, () -> {
            partie.abonner(vue);
            try {
                partie.rejoindre(joueurConnecte);
            } catch (TransitionInterditeException refus) {
                partie.desabonner(vue);
                throw refus;
            }
        });
        partieCourante = partie;
    }

    @Override
    public void refuserInvitation(PartieEntreJoueurs partie) {
        files.executer(partie, () -> partie.refuserInvitation(joueurConnecte));
    }

    @Override
    public void annulerPartie() {
        Partie partie = exigerPartieCourante();
        files.executer(partie, () -> partie.annuler(joueurConnecte));
    }

    @Override
    public Coup jouerCoup(String depart, String arrivee, TypePromotion promotion) {
        Partie partie = exigerPartieCourante();
        exigerNomDeCase(depart);
        exigerNomDeCase(arrivee);
        Coup coup = files.executer(partie, () -> {
            Plateau plateau = partie.plateau();
            return partie.jouerCoup(joueurConnecte, caseDe(plateau, depart), caseDe(plateau, arrivee), promotion);
        });
        lancerLOrdinateurSiCEstSonTour(partie);
        return coup;
    }

    @Override
    public void proposerNulle() {
        PartieEntreJoueurs partie = exigerPartieEntreJoueurs();
        files.executer(partie, () -> partie.proposerNulle(joueurConnecte));
    }

    @Override
    public void repondreNulle(boolean accepte) {
        PartieEntreJoueurs partie = exigerPartieEntreJoueurs();
        files.executer(partie, () -> {
            if (accepte) {
                partie.accepterNulle(joueurConnecte);
            } else {
                partie.refuserNulle(joueurConnecte);
            }
        });
    }

    @Override
    public void abandonner() {
        Partie partie = exigerPartieCourante();
        files.executer(partie, () -> partie.abandonner(joueurConnecte));
    }

    @Override
    public void demarrerPartieContreOrdinateur(int force, Couleur couleur) {
        exigerDePouvoirJouer();
        PartieContreOrdinateur partie = new PartieContreOrdinateur(joueurConnecte, couleur, force, horloge);
        suivre(partie);
        lancerLOrdinateurSiCEstSonTour(partie);
    }

    /** RG7: a finished game can be replayed by its participants only. */
    @Override
    public List<Coup> revoirPartie(Partie partie) {
        return files.executer(partie, () -> {
            boolean participe = partie.participations().stream()
                    .anyMatch(participation -> participation.joueur().equals(joueurConnecte));
            if (!participe || partie.etat() != EtatPartie.TERMINEE) {
                throw new ActionRefuseeException("Cette partie ne peut pas être revue");
            }
            return partie.coups();
        });
    }

    /** @return the game this player last entered, finished or not */
    public Optional<Partie> partieCourante() {
        return Optional.ofNullable(partieCourante);
    }

    private void suivre(Partie partie) {
        files.ajouter(partie);
        files.executer(partie, () -> {
            partie.abonner(archivage);
            partie.abonner(vue);
            depotParties.enregistrer(partie);
        });
        partieCourante = partie;
    }

    private void lancerLOrdinateurSiCEstSonTour(Partie partie) {
        if (partie instanceof PartieContreOrdinateur contreOrdinateur) {
            boolean aSonTour = files.executer(partie, () ->
                    partie.etat() == EtatPartie.EN_COURS && contreOrdinateur.ordinateurALeTrait());
            if (aSonTour) {
                tachesDeFond.execute(() -> faireJouerOrdinateur(contreOrdinateur));
            }
        }
    }

    private void faireJouerOrdinateur(PartieContreOrdinateur partie) {
        for (int tentative = 0; tentative < TENTATIVES_DU_MOTEUR; tentative++) {
            try {
                String position = files.executer(partie, partie::position);
                String coup = moteur.calculerCoup(position, partie.force());
                files.executer(partie, () -> jouerCoupDuMoteur(partie, coup));
                return;
            } catch (MoteurIndisponibleException | CoupRefuseException | IllegalArgumentException echec) {
                // E1, E2: no answer or an illegal move, the engine is asked once more.
                continue;
            } catch (TransitionInterditeException partieFinie) {
                // The player resigned while the engine was thinking: its move no longer matters.
                return;
            }
        }
        annulerSiToujoursEnCours(partie);
    }

    private void jouerCoupDuMoteur(PartieContreOrdinateur partie, String coupUci) {
        Plateau plateau = partie.plateau();
        Case depart = caseDe(plateau, coupUci.substring(0, FIN_DEPART_UCI));
        Case arrivee = caseDe(plateau, coupUci.substring(FIN_DEPART_UCI, FIN_ARRIVEE_UCI));
        TypePromotion promotion = coupUci.length() > FIN_ARRIVEE_UCI
                ? PROMOTIONS_UCI.get(coupUci.charAt(FIN_ARRIVEE_UCI))
                : null;
        partie.jouerCoupOrdinateur(depart, arrivee, promotion);
    }

    private void annulerSiToujoursEnCours(PartieContreOrdinateur partie) {
        files.executer(partie, () -> {
            if (partie.etat() == EtatPartie.EN_COURS) {
                partie.annulerParLeSysteme();
            }
        });
    }

    private void exigerDePouvoirJouer() {
        if (!joueurConnecte.peutJouer()) {
            throw new ActionRefuseeException(joueurConnecte.pseudo() + " ne peut pas commencer de partie");
        }
    }

    private Partie exigerPartieCourante() {
        if (partieCourante == null) {
            throw new ActionRefuseeException("Aucune partie en cours");
        }
        return partieCourante;
    }

    private PartieEntreJoueurs exigerPartieEntreJoueurs() {
        if (exigerPartieCourante() instanceof PartieEntreJoueurs partie) {
            return partie;
        }
        throw new ActionRefuseeException("Cette action n'existe que dans une partie entre joueurs");
    }

    private static void exigerNomDeCase(String nom) {
        if (nom == null || !NOM_DE_CASE.matcher(nom).matches()) {
            throw new ActionRefuseeException("Case inconnue : " + nom);
        }
    }

    private static Case caseDe(Plateau plateau, String nom) {
        return plateau.caseEn(nom.substring(0, 1), Integer.parseInt(nom.substring(1)));
    }
}
