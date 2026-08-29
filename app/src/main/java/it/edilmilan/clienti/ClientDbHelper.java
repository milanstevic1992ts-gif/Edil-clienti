package it.edilmilan.clienti;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.telephony.PhoneNumberUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClientDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "edil_clienti.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "clients";

    public ClientDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "first_name TEXT NOT NULL DEFAULT ''," +
                "last_name TEXT NOT NULL DEFAULT ''," +
                "phone TEXT NOT NULL DEFAULT ''," +
                "email TEXT NOT NULL DEFAULT ''," +
                "address TEXT NOT NULL DEFAULT ''," +
                "notes TEXT NOT NULL DEFAULT ''," +
                "contact_uri TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_clients_name ON " + TABLE + "(last_name, first_name)");
        db.execSQL("CREATE INDEX idx_clients_phone ON " + TABLE + "(phone)");
        db.execSQL("CREATE INDEX idx_clients_email ON " + TABLE + "(email)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migrazioni future: non eliminare mai i dati esistenti.
    }

    public long save(Client client) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues values = toValues(client);
        values.put("updated_at", now);
        if (client.id > 0) {
            db.update(TABLE, values, "id=?", new String[]{String.valueOf(client.id)});
            return client.id;
        }
        values.put("created_at", now);
        client.id = db.insertOrThrow(TABLE, null, values);
        return client.id;
    }

    public Client get(long id) {
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public List<Client> search(String query) {
        List<Client> clients = new ArrayList<>();
        String q = Client.safe(query);
        String selection = null;
        String[] args = null;
        if (!q.isEmpty()) {
            String like = "%" + q + "%";
            selection = "first_name LIKE ? OR last_name LIKE ? OR phone LIKE ? OR email LIKE ? OR address LIKE ? OR notes LIKE ?";
            args = new String[]{like, like, like, like, like, like};
        }
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, selection, args,
                null, null, "last_name COLLATE NOCASE, first_name COLLATE NOCASE")) {
            while (cursor.moveToNext()) clients.add(fromCursor(cursor));
        }
        return clients;
    }

    public Client findDuplicate(String phone, String email) {
        String normalizedPhone = normalizePhone(phone);
        String normalizedEmail = Client.safe(email).toLowerCase(Locale.ROOT);
        for (Client client : search("")) {
            if (!normalizedPhone.isEmpty() && normalizedPhone.equals(normalizePhone(client.phone))) return client;
            if (!normalizedEmail.isEmpty() && normalizedEmail.equals(Client.safe(client.email).toLowerCase(Locale.ROOT))) return client;
        }
        return null;
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public String exportJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "EDIL Clienti");
        root.put("formatVersion", 1);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray rows = new JSONArray();
        for (Client client : search("")) rows.put(toJson(client));
        root.put("clients", rows);
        return root.toString(2);
    }

    public int importJson(String json, boolean replaceAll) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray rows = root.getJSONArray("clients");
        SQLiteDatabase db = getWritableDatabase();
        int imported = 0;
        db.beginTransaction();
        try {
            if (replaceAll) db.delete(TABLE, null, null);
            for (int i = 0; i < rows.length(); i++) {
                Client incoming = fromJson(rows.getJSONObject(i));
                Client duplicate = findDuplicate(incoming.phone, incoming.email);
                if (duplicate != null) incoming.id = duplicate.id;
                save(incoming);
                imported++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return imported;
    }

    public static String normalizePhone(String phone) {
        String normalized = PhoneNumberUtils.normalizeNumber(Client.safe(phone));
        if (normalized.startsWith("00")) normalized = "+" + normalized.substring(2);
        return normalized;
    }

    private ContentValues toValues(Client c) {
        ContentValues values = new ContentValues();
        values.put("first_name", Client.safe(c.firstName));
        values.put("last_name", Client.safe(c.lastName));
        values.put("phone", Client.safe(c.phone));
        values.put("email", Client.safe(c.email));
        values.put("address", Client.safe(c.address));
        values.put("notes", Client.safe(c.notes));
        values.put("contact_uri", Client.safe(c.contactUri));
        if (c.createdAt > 0) values.put("created_at", c.createdAt);
        return values;
    }

    private Client fromCursor(Cursor cursor) {
        Client c = new Client();
        c.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        c.firstName = cursor.getString(cursor.getColumnIndexOrThrow("first_name"));
        c.lastName = cursor.getString(cursor.getColumnIndexOrThrow("last_name"));
        c.phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"));
        c.email = cursor.getString(cursor.getColumnIndexOrThrow("email"));
        c.address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
        c.notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"));
        c.contactUri = cursor.getString(cursor.getColumnIndexOrThrow("contact_uri"));
        c.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        c.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return c;
    }

    private JSONObject toJson(Client c) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("firstName", c.firstName);
        item.put("lastName", c.lastName);
        item.put("phone", c.phone);
        item.put("email", c.email);
        item.put("address", c.address);
        item.put("notes", c.notes);
        item.put("contactUri", c.contactUri);
        item.put("createdAt", c.createdAt);
        item.put("updatedAt", c.updatedAt);
        return item;
    }

    private Client fromJson(JSONObject item) {
        Client c = new Client();
        c.firstName = item.optString("firstName", "");
        c.lastName = item.optString("lastName", "");
        c.phone = item.optString("phone", "");
        c.email = item.optString("email", "");
        c.address = item.optString("address", "");
        c.notes = item.optString("notes", "");
        c.contactUri = item.optString("contactUri", "");
        c.createdAt = item.optLong("createdAt", System.currentTimeMillis());
        c.updatedAt = item.optLong("updatedAt", System.currentTimeMillis());
        return c;
    }
}
