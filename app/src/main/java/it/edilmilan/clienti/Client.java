package it.edilmilan.clienti;

public class Client {
    public long id;
    public String firstName = "";
    public String lastName = "";
    public String phone = "";
    public String email = "";
    public String address = "";
    public String notes = "";
    public String contactUri = "";
    public long createdAt;
    public long updatedAt;

    public String fullName() {
        return (safe(firstName) + " " + safe(lastName)).trim();
    }

    public static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
