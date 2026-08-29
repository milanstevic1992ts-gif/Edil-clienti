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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "edil_clienti.db";
    private static final int DB_VERSION = 3;
    private static final String TABLE = "clients";
    private static final String INTERACTIONS = "relationship_interactions";
    private static final String OUTBOX = "outbox_events";

    public ClientDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ge360_id TEXT NOT NULL DEFAULT ''," +
                "ge360_jobsite_id TEXT NOT NULL DEFAULT ''," +
                "first_name TEXT NOT NULL DEFAULT ''," +
                "last_name TEXT NOT NULL DEFAULT ''," +
                "phone TEXT NOT NULL DEFAULT ''," +
                "email TEXT NOT NULL DEFAULT ''," +
                "address TEXT NOT NULL DEFAULT ''," +
                "notes TEXT NOT NULL DEFAULT ''," +
                "contact_uri TEXT NOT NULL DEFAULT ''," +
                "temperature TEXT NOT NULL DEFAULT 'Da coltivare'," +
                "ai_temperature TEXT NOT NULL DEFAULT 'Non analizzata'," +
                "relationship_phase TEXT NOT NULL DEFAULT 'Nuovo contatto'," +
                "pulse INTEGER NOT NULL DEFAULT 50," +
                "birthday TEXT NOT NULL DEFAULT ''," +
                "follow_up TEXT NOT NULL DEFAULT ''," +
                "last_interaction_at INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_clients_name ON " + TABLE + "(last_name, first_name)");
        db.execSQL("CREATE INDEX idx_clients_phone ON " + TABLE + "(phone)");
        db.execSQL("CREATE INDEX idx_clients_email ON " + TABLE + "(email)");
        createIdentityIndexes(db);
        createInteractionsTable(db);
        createOutboxTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN temperature TEXT NOT NULL DEFAULT 'Da coltivare'");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN ai_temperature TEXT NOT NULL DEFAULT 'Non analizzata'");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN relationship_phase TEXT NOT NULL DEFAULT 'Nuovo contatto'");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN pulse INTEGER NOT NULL DEFAULT 50");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN birthday TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN follow_up TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN last_interaction_at INTEGER NOT NULL DEFAULT 0");
            createInteractionsTable(db);
            createOutboxTable(db);
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN ge360_id TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN ge360_jobsite_id TEXT NOT NULL DEFAULT ''");
            fillMissingGe360Identities(db);
            createIdentityIndexes(db);
        }
    }

    private void createIdentityIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_clients_ge360_id ON " + TABLE + "(ge360_id) WHERE ge360_id <> ''");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_clients_ge360_jobsite ON " + TABLE + "(ge360_jobsite_id) WHERE ge360_jobsite_id <> ''");
    }

    private void fillMissingGe360Identities(SQLiteDatabase db) {
        try (Cursor cursor = db.query(TABLE, new String[]{"id", "ge360_id", "ge360_jobsite_id"}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String ge360Id = cursor.getString(1);
                String jobsiteId = cursor.getString(2);
                if (Client.safe(ge360Id).isEmpty() || Client.safe(jobsiteId).isEmpty()) {
                    ContentValues values = new ContentValues();
                    if (Client.safe(ge360Id).isEmpty()) values.put("ge360_id", UUID.randomUUID().toString());
                    if (Client.safe(jobsiteId).isEmpty()) values.put("ge360_jobsite_id", UUID.randomUUID().toString());
                    db.update(TABLE, values, "id=?", new String[]{String.valueOf(id)});
                }
            }
        }
    }

    private void createInteractionsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + INTERACTIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "client_id INTEGER NOT NULL," +
                "signal TEXT NOT NULL DEFAULT ''," +
                "reason TEXT NOT NULL DEFAULT ''," +
                "detail TEXT NOT NULL DEFAULT ''," +
                "delta INTEGER NOT NULL DEFAULT 0," +
                "pulse_after INTEGER NOT NULL DEFAULT 50," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_interactions_client ON " + INTERACTIONS + "(client_id, created_at DESC)");
    }

    private void createOutboxTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + OUTBOX + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "event_type TEXT NOT NULL," +
                "payload TEXT NOT NULL," +
                "status TEXT NOT NULL DEFAULT 'pending'," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_status ON " + OUTBOX + "(status, created_at)");
    }

    public long save(Client client) {
        SQLiteDatabase db = getWritableDatabase();
        ensureIdentity(client);
        long now = System.currentTimeMillis();
        ContentValues values = toValues(client);
        values.put("updated_at", now);
        client.updatedAt = now;
        if (client.id > 0) {
            db.update(TABLE, values, "id=?", new String[]{String.valueOf(client.id)});
            return client.id;
        }
        values.put("created_at", now);
        client.createdAt = now;
        client.id = db.insertOrThrow(TABLE, null, values);
        return client.id;
    }

    private void ensureIdentity(Client client) {
        if (Client.safe(client.ge360Id).isEmpty()) client.ge360Id = UUID.randomUUID().toString();
        if (Client.safe(client.ge360JobsiteId).isEmpty()) client.ge360JobsiteId = UUID.randomUUID().toString();
    }

    public Client get(long id) {
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public Client getByGe360Id(String ge360Id) {
        String value = Client.safe(ge360Id);
        if (value.isEmpty()) return null;
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, "ge360_id=?",
                new String[]{value}, null, null, null, "1")) {
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
            selection = "first_name LIKE ? OR last_name LIKE ? OR phone LIKE ? OR email LIKE ? OR address LIKE ? OR notes LIKE ? OR temperature LIKE ? OR relationship_phase LIKE ?";
            args = new String[]{like, like, like, like, like, like, like, like};
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
        getWritableDatabase().delete(INTERACTIONS, "client_id=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateTemperature(long clientId, String temperature) {
        Client current = get(clientId);
        if (current == null || Client.safe(current.temperature).equals(Client.safe(temperature))) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put("temperature", Client.safe(temperature));
            values.put("updated_at", now);
            db.update(TABLE, values, "id=?", new String[]{String.valueOf(clientId)});
            JSONObject payload = new JSONObject();
            payload.put("clientId", current.ge360Id);
            payload.put("localClientId", clientId);
            payload.put("jobsiteId", current.ge360JobsiteId);
            payload.put("temperature", Client.safe(temperature));
            payload.put("createdAt", now);
            enqueueOutbox(db, "client.temperature.changed", payload.toString(), now);
            db.setTransactionSuccessful();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        } finally {
            db.endTransaction();
        }
    }

    public int addInteraction(long clientId, String signal, String reason, String detail, int delta) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Client client = get(clientId);
            if (client == null) return 50;
            int newPulse = Client.clampPulse(client.pulse + delta);
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put("client_id", clientId);
            values.put("signal", Client.safe(signal));
            values.put("reason", Client.safe(reason));
            values.put("detail", Client.safe(detail));
            values.put("delta", newPulse - client.pulse);
            values.put("pulse_after", newPulse);
            values.put("created_at", now);
            db.insertOrThrow(INTERACTIONS, null, values);
            JSONObject payload = new JSONObject();
            payload.put("clientId", client.ge360Id);
            payload.put("localClientId", clientId);
            payload.put("jobsiteId", client.ge360JobsiteId);
            payload.put("signal", Client.safe(signal));
            payload.put("reason", Client.safe(reason));
            payload.put("detail", Client.safe(detail));
            payload.put("delta", newPulse - client.pulse);
            payload.put("pulseAfter", newPulse);
            payload.put("createdAt", now);
            enqueueOutbox(db, "relationship.signal.created", payload.toString(), now);
            ContentValues update = new ContentValues();
            update.put("pulse", newPulse);
            update.put("last_interaction_at", now);
            update.put("updated_at", now);
            db.update(TABLE, update, "id=?", new String[]{String.valueOf(clientId)});
            db.setTransactionSuccessful();
            return newPulse;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        } finally {
            db.endTransaction();
        }
    }

    public void recordPulseCorrection(long clientId, int oldPulse, int newPulse) {
        int delta = Client.clampPulse(newPulse) - Client.clampPulse(oldPulse);
        if (delta == 0) return;
        ContentValues values = new ContentValues();
        values.put("client_id", clientId);
        values.put("signal", "Correzione manuale");
        values.put("reason", "Correzione");
        values.put("detail", "Polso corretto manualmente");
        values.put("delta", delta);
        values.put("pulse_after", Client.clampPulse(newPulse));
        long now = System.currentTimeMillis();
        values.put("created_at", now);
        SQLiteDatabase db = getWritableDatabase();
        db.insertOrThrow(INTERACTIONS, null, values);
        ContentValues update = new ContentValues();
        update.put("last_interaction_at", now);
        update.put("updated_at", now);
        db.update(TABLE, update, "id=?", new String[]{String.valueOf(clientId)});
    }

    public List<RelationshipInteraction> getInteractions(long clientId, int limit) {
        List<RelationshipInteraction> rows = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(INTERACTIONS, null, "client_id=?",
                new String[]{String.valueOf(clientId)}, null, null, "created_at DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) rows.add(interactionFromCursor(cursor));
        }
        return rows;
    }

    public String exportJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "EDIL Clienti");
        root.put("formatVersion", 3);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray rows = new JSONArray();
        for (Client client : search("")) rows.put(toJson(client));
        root.put("clients", rows);
        JSONArray history = new JSONArray();
        try (Cursor cursor = getReadableDatabase().query(INTERACTIONS, null, null, null, null, null, "created_at ASC")) {
            while (cursor.moveToNext()) history.put(interactionToJson(interactionFromCursor(cursor)));
        }
        root.put("relationshipInteractions", history);
        return root.toString(2);
    }

    public int importJson(String json, boolean replaceAll) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray rows = root.getJSONArray("clients");
        JSONArray history = root.optJSONArray("relationshipInteractions");
        SQLiteDatabase db = getWritableDatabase();
        int imported = 0;
        Map<Long, Long> idMap = new HashMap<>();
        db.beginTransaction();
        try {
            if (replaceAll) {
                db.delete(INTERACTIONS, null, null);
                db.delete(TABLE, null, null);
            }
            for (int i = 0; i < rows.length(); i++) {
                JSONObject source = rows.getJSONObject(i);
                Client incoming = fromJson(source);
                Client duplicate = findDuplicate(incoming.phone, incoming.email);
                if (duplicate != null) {
                    incoming.id = duplicate.id;
                    if (!Client.safe(duplicate.ge360Id).isEmpty()) incoming.ge360Id = duplicate.ge360Id;
                    if (!Client.safe(duplicate.ge360JobsiteId).isEmpty()) incoming.ge360JobsiteId = duplicate.ge360JobsiteId;
                }
                long newId = save(incoming);
                idMap.put(source.optLong("backupId", 0), newId);
                imported++;
            }
            if (history != null) {
                for (int i = 0; i < history.length(); i++) {
                    JSONObject item = history.getJSONObject(i);
                    long newClientId = idMap.getOrDefault(item.optLong("clientId", 0), 0L);
                    if (newClientId > 0 && !interactionExists(newClientId, item.optLong("createdAt", 0), item.optString("signal", ""))) {
                        insertInteractionJson(db, newClientId, item);
                    }
                }
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
        values.put("ge360_id", Client.safe(c.ge360Id));
        values.put("ge360_jobsite_id", Client.safe(c.ge360JobsiteId));
        values.put("first_name", Client.safe(c.firstName));
        values.put("last_name", Client.safe(c.lastName));
        values.put("phone", Client.safe(c.phone));
        values.put("email", Client.safe(c.email));
        values.put("address", Client.safe(c.address));
        values.put("notes", Client.safe(c.notes));
        values.put("contact_uri", Client.safe(c.contactUri));
        values.put("temperature", Client.safe(c.temperature).isEmpty() ? "Da coltivare" : Client.safe(c.temperature));
        values.put("ai_temperature", Client.safe(c.aiTemperature).isEmpty() ? "Non analizzata" : Client.safe(c.aiTemperature));
        values.put("relationship_phase", Client.safe(c.relationshipPhase).isEmpty() ? "Nuovo contatto" : Client.safe(c.relationshipPhase));
        values.put("pulse", Client.clampPulse(c.pulse));
        values.put("birthday", Client.safe(c.birthday));
        values.put("follow_up", Client.safe(c.followUp));
        values.put("last_interaction_at", c.lastInteractionAt);
        if (c.createdAt > 0) values.put("created_at", c.createdAt);
        return values;
    }

    private Client fromCursor(Cursor cursor) {
        Client c = new Client();
        c.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        c.ge360Id = cursor.getString(cursor.getColumnIndexOrThrow("ge360_id"));
        c.ge360JobsiteId = cursor.getString(cursor.getColumnIndexOrThrow("ge360_jobsite_id"));
        c.firstName = cursor.getString(cursor.getColumnIndexOrThrow("first_name"));
        c.lastName = cursor.getString(cursor.getColumnIndexOrThrow("last_name"));
        c.phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"));
        c.email = cursor.getString(cursor.getColumnIndexOrThrow("email"));
        c.address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
        c.notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"));
        c.contactUri = cursor.getString(cursor.getColumnIndexOrThrow("contact_uri"));
        c.temperature = cursor.getString(cursor.getColumnIndexOrThrow("temperature"));
        c.aiTemperature = cursor.getString(cursor.getColumnIndexOrThrow("ai_temperature"));
        c.relationshipPhase = cursor.getString(cursor.getColumnIndexOrThrow("relationship_phase"));
        c.pulse = cursor.getInt(cursor.getColumnIndexOrThrow("pulse"));
        c.birthday = cursor.getString(cursor.getColumnIndexOrThrow("birthday"));
        c.followUp = cursor.getString(cursor.getColumnIndexOrThrow("follow_up"));
        c.lastInteractionAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_interaction_at"));
        c.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        c.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return c;
    }

    private JSONObject toJson(Client c) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("backupId", c.id);
        item.put("ge360Id", c.ge360Id);
        item.put("ge360JobsiteId", c.ge360JobsiteId);
        item.put("firstName", c.firstName);
        item.put("lastName", c.lastName);
        item.put("phone", c.phone);
        item.put("email", c.email);
        item.put("address", c.address);
        item.put("notes", c.notes);
        item.put("contactUri", c.contactUri);
        item.put("temperature", c.temperature);
        item.put("aiTemperature", c.aiTemperature);
        item.put("relationshipPhase", c.relationshipPhase);
        item.put("pulse", c.pulse);
        item.put("birthday", c.birthday);
        item.put("followUp", c.followUp);
        item.put("lastInteractionAt", c.lastInteractionAt);
        item.put("createdAt", c.createdAt);
        item.put("updatedAt", c.updatedAt);
        return item;
    }

    private Client fromJson(JSONObject item) {
        Client c = new Client();
        c.ge360Id = item.optString("ge360Id", "");
        c.ge360JobsiteId = item.optString("ge360JobsiteId", "");
        c.firstName = item.optString("firstName", "");
        c.lastName = item.optString("lastName", "");
        c.phone = item.optString("phone", "");
        c.email = item.optString("email", "");
        c.address = item.optString("address", "");
        c.notes = item.optString("notes", "");
        c.contactUri = item.optString("contactUri", "");
        c.temperature = item.optString("temperature", "Da coltivare");
        c.aiTemperature = item.optString("aiTemperature", "Non analizzata");
        c.relationshipPhase = item.optString("relationshipPhase", "Nuovo contatto");
        c.pulse = Client.clampPulse(item.optInt("pulse", 50));
        c.birthday = item.optString("birthday", "");
        c.followUp = item.optString("followUp", "");
        c.lastInteractionAt = item.optLong("lastInteractionAt", 0);
        c.createdAt = item.optLong("createdAt", System.currentTimeMillis());
        c.updatedAt = item.optLong("updatedAt", System.currentTimeMillis());
        ensureIdentity(c);
        return c;
    }

    private RelationshipInteraction interactionFromCursor(Cursor cursor) {
        RelationshipInteraction row = new RelationshipInteraction();
        row.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        row.clientId = cursor.getLong(cursor.getColumnIndexOrThrow("client_id"));
        row.signal = cursor.getString(cursor.getColumnIndexOrThrow("signal"));
        row.reason = cursor.getString(cursor.getColumnIndexOrThrow("reason"));
        row.detail = cursor.getString(cursor.getColumnIndexOrThrow("detail"));
        row.delta = cursor.getInt(cursor.getColumnIndexOrThrow("delta"));
        row.pulseAfter = cursor.getInt(cursor.getColumnIndexOrThrow("pulse_after"));
        row.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        return row;
    }

    private JSONObject interactionToJson(RelationshipInteraction row) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("clientId", row.clientId);
        item.put("signal", row.signal);
        item.put("reason", row.reason);
        item.put("detail", row.detail);
        item.put("delta", row.delta);
        item.put("pulseAfter", row.pulseAfter);
        item.put("createdAt", row.createdAt);
        return item;
    }

    private boolean interactionExists(long clientId, long createdAt, String signal) {
        try (Cursor cursor = getReadableDatabase().query(INTERACTIONS, new String[]{"id"},
                "client_id=? AND created_at=? AND signal=?",
                new String[]{String.valueOf(clientId), String.valueOf(createdAt), signal}, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    private void insertInteractionJson(SQLiteDatabase db, long clientId, JSONObject item) {
        ContentValues values = new ContentValues();
        values.put("client_id", clientId);
        values.put("signal", item.optString("signal", ""));
        values.put("reason", item.optString("reason", ""));
        values.put("detail", item.optString("detail", ""));
        values.put("delta", item.optInt("delta", 0));
        values.put("pulse_after", Client.clampPulse(item.optInt("pulseAfter", 50)));
        values.put("created_at", item.optLong("createdAt", System.currentTimeMillis()));
        db.insert(INTERACTIONS, null, values);
    }

    private void enqueueOutbox(SQLiteDatabase db, String eventType, String payload, long createdAt) {
        ContentValues event = new ContentValues();
        event.put("event_type", eventType);
        event.put("payload", payload);
        event.put("status", "pending");
        event.put("created_at", createdAt);
        db.insertOrThrow(OUTBOX, null, event);
    }
}
