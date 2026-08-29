package it.edilmilan.clienti;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClientRelationshipRulesTest {
    @Test public void pulseIsAlwaysClampedBetweenZeroAndOneHundred() {
        assertEquals(0, Client.clampPulse(-20));
        assertEquals(50, Client.clampPulse(50));
        assertEquals(100, Client.clampPulse(130));
    }

    @Test public void signalsUseGradualAndPredictableChanges() {
        assertEquals(6, RelationshipRules.deltaFor("Positivo"));
        assertEquals(3, RelationshipRules.deltaFor("Curioso"));
        assertEquals(0, RelationshipRules.deltaFor("Neutro"));
        assertEquals(-4, RelationshipRules.deltaFor("Dubbioso"));
        assertEquals(-8, RelationshipRules.deltaFor("Negativo"));
        assertEquals(-12, RelationshipRules.deltaFor("Teso"));
    }
}
