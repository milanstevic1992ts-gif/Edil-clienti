package it.edilmilan.clienti;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class AutomaticBackupManager {
    public static final int MAX_BACKUPS = 10;
    private static final String PREFS = "automatic_backup_settings";
    private static final String KEY_DRIVE_TREE_URI = "drive_tree_uri";
    private static final String FILE_PREFIX = "EDIL-Clienti-auto_";

    private AutomaticBackupManager() { }

    public static void configureDriveFolder(Context context, Uri treeUri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_DRIVE_TREE_URI, treeUri.toString()).apply();
    }

    public static Uri getDriveFolder(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = preferences.getString(KEY_DRIVE_TREE_URI, "");
        return value == null || value.isEmpty() ? null : Uri.parse(value);
    }

    public static Result perform(Context context) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ITALY).format(new Date());
        String fileName = FILE_PREFIX + timestamp + ".json";
        Result result = new Result();
        String json;
        try {
            json = new ClientDbHelper(context.getApplicationContext()).exportJson();
        } catch (Exception e) {
            result.localError = "Impossibile leggere l'archivio clienti";
            return result;
        }

        try {
            File directory = localBackupDirectory(context);
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cartella non disponibile");
            File temporary = new File(directory, fileName + ".tmp");
            File destination = new File(directory, fileName);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete();
                throw new IllegalStateException("Salvataggio non completato");
            }
            rotateLocal(directory);
            result.localSuccess = true;
            result.localPath = directory.getAbsolutePath();
        } catch (Exception e) {
            result.localError = e.getMessage();
        }

        Uri driveFolder = getDriveFolder(context);
        result.driveConfigured = driveFolder != null;
        if (driveFolder != null) {
            try {
                writeToDocumentTree(context, driveFolder, fileName, json);
                rotateDocumentTree(context, driveFolder);
                result.driveSuccess = true;
            } catch (Exception e) {
                result.driveError = e.getMessage();
            }
        }
        return result;
    }

    public static File latestLocalBackup(Context context) {
        File[] backups = localBackupDirectory(context)
                .listFiles((dir, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(".json"));
        if (backups == null || backups.length == 0) return null;
        java.util.Arrays.sort(backups, Comparator.comparingLong((File file) -> file.lastModified()).reversed());
        return backups[0];
    }

    private static File localBackupDirectory(Context context) {
        File documents = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (documents == null) documents = context.getFilesDir();
        return new File(documents, "EDIL-Clienti/Backups");
    }

    private static void rotateLocal(File directory) {
        File[] backups = directory.listFiles((dir, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(".json"));
        if (backups == null || backups.length <= MAX_BACKUPS) return;
        java.util.Arrays.sort(backups, Comparator.comparingLong((File file) -> file.lastModified()));
        for (int i = 0; i < backups.length - MAX_BACKUPS; i++) backups[i].delete();
    }

    private static void writeToDocumentTree(Context context, Uri treeUri, String fileName, String json) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        String treeId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri folder = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId);
        Uri file = DocumentsContract.createDocument(resolver, folder, "application/json", fileName);
        if (file == null) throw new IllegalStateException("Google Drive non ha creato il file");
        try (OutputStream output = resolver.openOutputStream(file, "w")) {
            if (output == null) throw new IllegalStateException("Google Drive non disponibile");
            output.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void rotateDocumentTree(Context context, Uri treeUri) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        String treeId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        List<RemoteBackup> backups = new ArrayList<>();
        try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
            if (cursor == null) throw new IllegalStateException("Cartella Google Drive non leggibile");
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (name != null && name.startsWith(FILE_PREFIX) && name.endsWith(".json")) {
                    backups.add(new RemoteBackup(cursor.getString(0), cursor.getLong(2)));
                }
            }
        }
        backups.sort(Comparator.comparingLong((RemoteBackup item) -> item.modified));
        for (int i = 0; i < backups.size() - MAX_BACKUPS; i++) {
            Uri document = DocumentsContract.buildDocumentUriUsingTree(treeUri, backups.get(i).documentId);
            DocumentsContract.deleteDocument(resolver, document);
        }
    }

    private static class RemoteBackup {
        final String documentId;
        final long modified;

        RemoteBackup(String documentId, long modified) {
            this.documentId = documentId;
            this.modified = modified;
        }
    }

    public static class Result {
        public boolean localSuccess;
        public boolean driveConfigured;
        public boolean driveSuccess;
        public String localPath = "";
        public String localError = "";
        public String driveError = "";

        public String summary() {
            String local = localSuccess ? "Telefono: salvato" : "Telefono: errore";
            String drive;
            if (!driveConfigured) drive = "Drive: non configurato";
            else drive = driveSuccess ? "Drive: salvato" : "Drive: non disponibile";
            return local + " · " + drive + "\nConservati gli ultimi " + MAX_BACKUPS + " backup per destinazione.";
        }
    }
}
