package echecs.controle;

import echecs.metier.joueur.Joueur;
import echecs.metier.partie.EtatPartie;
import echecs.metier.partie.Participation;
import echecs.metier.partie.Partie;
import echecs.metier.partie.PartieEntreJoueurs;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake repositories for the tests, kept in memory. */
final class DepotsEnMemoire {
    private DepotsEnMemoire() {
    }

    static final class Joueurs implements DepotJoueurs {
        final List<Joueur> joueurs = new CopyOnWriteArrayList<>();
        int enregistrements = 0;

        @Override
        public void enregistrer(Joueur joueur) {
            enregistrements++;
            if (!joueurs.contains(joueur)) {
                joueurs.add(joueur);
            }
        }

        @Override
        public Optional<Joueur> trouverParEmail(String email) {
            return joueurs.stream().filter(joueur -> joueur.email().equals(email)).findFirst();
        }

        @Override
        public boolean pseudoExiste(String pseudo) {
            return joueurs.stream().anyMatch(joueur -> joueur.pseudo().equals(pseudo));
        }

        @Override
        public List<Joueur> tous() {
            return List.copyOf(joueurs);
        }
    }

    static final class Parties implements DepotParties {
        private final Set<Partie> parties = new LinkedHashSet<>();
        int enregistrements = 0;

        @Override
        public synchronized void enregistrer(Partie partie) {
            enregistrements++;
            parties.add(partie);
        }

        @Override
        public synchronized List<PartieEntreJoueurs> partiesOuvertes() {
            List<PartieEntreJoueurs> ouvertes = new ArrayList<>();
            for (Partie partie : parties) {
                if (partie instanceof PartieEntreJoueurs entreJoueurs && partie.etat() == EtatPartie.EN_ATTENTE) {
                    ouvertes.add(entreJoueurs);
                }
            }
            return ouvertes;
        }

        @Override
        public synchronized Optional<Partie> partieActiveDe(Joueur joueur) {
            return parties.stream()
                    .filter(partie -> partie.etat() == EtatPartie.EN_ATTENTE || partie.etat() == EtatPartie.EN_COURS)
                    .filter(partie -> participe(partie, joueur))
                    .findFirst();
        }

        @Override
        public synchronized List<Partie> partiesTermineesDe(Joueur joueur) {
            return parties.stream()
                    .filter(partie -> partie.etat() == EtatPartie.TERMINEE)
                    .filter(partie -> participe(partie, joueur))
                    .toList();
        }

        private static boolean participe(Partie partie, Joueur joueur) {
            return partie.participations().stream().map(Participation::joueur).anyMatch(joueur::equals);
        }
    }
}
