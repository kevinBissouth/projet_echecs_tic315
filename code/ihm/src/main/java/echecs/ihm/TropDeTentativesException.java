package echecs.ihm;

class TropDeTentativesException extends RuntimeException {
    TropDeTentativesException() {
        super("Trop de tentatives. Réessayez dans une minute.");
    }
}
