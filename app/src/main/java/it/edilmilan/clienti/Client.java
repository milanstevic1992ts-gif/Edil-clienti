package it.edilmilan.clienti;

public class Client {
    public long id;
    public String ge360Id = "";
    public String ge360JobsiteId = "";
    public String firstName = "";
    public String lastName = "";
    public String phone = "";
    public String email = "";
    public String address = "";
    public String notes = "";
    public String contactUri = "";
    public String temperature = "Da coltivare";
    public String aiTemperature = "Non analizzata";
    public String relationshipPhase = "Nuovo contatto";
    public int pulse = 50;
    public String birthday = "";
    public String followUp = "";
    public long lastInteractionAt;
    public long createdAt;
    public long updatedAt;

    public String fullName() {
        return (safe(firstName) + " " + safe(lastName)).trim();
    }

    public static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static int clampPulse(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
