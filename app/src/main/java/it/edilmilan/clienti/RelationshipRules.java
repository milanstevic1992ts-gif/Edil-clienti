package it.edilmilan.clienti;

public final class RelationshipRules {
    private RelationshipRules() { }

    public static int deltaFor(String signal) {
        switch (Client.safe(signal)) {
            case "Positivo": return 6;
            case "Curioso": return 3;
            case "Dubbioso": return -4;
            case "Negativo": return -8;
            case "Teso": return -12;
            case "Neutro":
            default: return 0;
        }
    }
}
