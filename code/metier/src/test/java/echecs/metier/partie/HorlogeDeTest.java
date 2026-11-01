package echecs.metier.partie;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the tests move by hand, so that no test has to wait. */
final class HorlogeDeTest extends Clock {
    private Instant maintenant = Instant.parse("2026-10-16T20:00:00Z");

    void avancer(Duration duree) {
        maintenant = maintenant.plus(duree);
    }

    @Override
    public Instant instant() {
        return maintenant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
