package it.edilmilan.clienti;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements ClientAdapter.Listener {
    private static final int REQUEST_EDIT = 10;
    private static final int REQUEST_PICK_CONTACT = 20;
    private static final int REQUEST_CONTACT_PERMISSION = 21;
    private static final int REQUEST_BACKUP = 30;
    private static final int REQUEST_RESTORE = 31;

    private ClientDbHelper db;
    private ClientAdapter adapter;
    private EditText searchInput;
    private TextView countLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new ClientDbHelper(this);

        searchInput = findViewById(R.id.searchInput);
        countLabel = findViewById(R.id.countLabel);
        ListView list = findViewById(R.id.clientList);
        View empty = findViewById(R.id.emptyView);
        adapter = new ClientAdapter(this, this);
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        findViewById(R.id.addButton).setOnClickListener(v -> openEditor(null, null));
        findViewById(R.id.importButton).setOnClickListener(v -> startContactImport());
        findViewById(R.id.menuButton).setOnClickListener(this::showDataMenu);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { loadClients(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        loadClients();
    }

    private void showDataMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Crea backup");
        popup.getMenu().add(0, 2, 1, "Ripristina backup");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) createBackup();
            else if (item.getItemId() == 2) chooseBackup();
            return true;
        });
        popup.show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) loadClients();
    }

    private void loadClients() {
        List<Client> clients = db.search(searchInput == null ? "" : searchInput.getText().toString());
        adapter.setClients(clients);
        countLabel.setText(clients.size() + (clients.size() == 1 ? " cliente" : " clienti"));
    }

    private void openEditor(Client existing, Client prefill) {
        Intent intent = new Intent(this, EditClientActivity.class);
        if (existing != null) intent.putExtra(EditClientActivity.EXTRA_ID, existing.id);
        if (prefill != null) {
            intent.putExtra(EditClientActivity.EXTRA_FIRST_NAME, prefill.firstName);
            intent.putExtra(EditClientActivity.EXTRA_LAST_NAME, prefill.lastName);
            intent.putExtra(EditClientActivity.EXTRA_PHONE, prefill.phone);
            intent.putExtra(EditClientActivity.EXTRA_EMAIL, prefill.email);
            intent.putExtra(EditClientActivity.EXTRA_ADDRESS, prefill.address);
            intent.putExtra(EditClientActivity.EXTRA_CONTACT_URI, prefill.contactUri);
        }
        startActivityForResult(intent, REQUEST_EDIT);
    }

    private void startContactImport() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACT_PERMISSION);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        try {
            startActivityForResult(intent, REQUEST_PICK_CONTACT);
        } catch (Exception e) {
            Toast.makeText(this, "Nessuna app Rubrica disponibile", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CONTACT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startContactImport();
            else Toast.makeText(this, "Permesso rubrica necessario per importare un contatto", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQUEST_PICK_CONTACT && data.getData() != null) {
            importSelectedContact(data.getData());
        } else if (requestCode == REQUEST_BACKUP && data.getData() != null) {
            writeBackup(data.getData());
        } else if (requestCode == REQUEST_RESTORE && data.getData() != null) {
            askRestoreMode(data.getData());
        }
    }

    private void importSelectedContact(Uri contactUri) {
        try {
            Client imported = readContact(contactUri);
            Client duplicate = db.findDuplicate(imported.phone, imported.email);
            if (duplicate == null) {
                openEditor(null, imported);
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Cliente già presente")
                    .setMessage("Esiste già “" + duplicate.fullName() + "” con lo stesso telefono o email. Vuoi aggiornarlo con i dati della rubrica?")
                    .setPositiveButton("Aggiorna", (d, w) -> openEditor(duplicate, imported))
                    .setNeutralButton("Crea comunque", (d, w) -> openEditor(null, imported))
                    .setNegativeButton("Annulla", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Impossibile leggere il contatto selezionato", Toast.LENGTH_LONG).show();
        }
    }

    private Client readContact(Uri uri) {
        Client client = new Client();
        client.contactUri = uri.toString();
        String contactId = "";
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                contactId = cursor.getString(0);
                splitName(cursor.getString(1), client);
            }
        }
        if (contactId.isEmpty()) return client;
        ContentResolver resolver = getContentResolver();
        client.phone = firstValue(resolver, ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", contactId);
        client.email = firstValue(resolver, ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?", contactId);
        client.address = firstValue(resolver, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID + "=?", contactId);
        return client;
    }

    private String firstValue(ContentResolver resolver, Uri uri, String column, String selection, String contactId) {
        try (Cursor cursor = resolver.query(uri, new String[]{column}, selection,
                new String[]{contactId}, null)) {
            if (cursor != null && cursor.moveToFirst()) return Client.safe(cursor.getString(0));
        }
        return "";
    }

    private void splitName(String displayName, Client client) {
        String name = Client.safe(displayName);
        int firstSpace = name.indexOf(' ');
        if (firstSpace < 0) client.firstName = name;
        else {
            client.firstName = name.substring(0, firstSpace).trim();
            client.lastName = name.substring(firstSpace + 1).trim();
        }
    }

    private void createBackup() {
        String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ITALY).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "EDIL-Clienti-backup_" + date + ".json");
        startActivityForResult(intent, REQUEST_BACKUP);
    }

    private void chooseBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_RESTORE);
    }

    private void writeBackup(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Output non disponibile");
            out.write(db.exportJson().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Backup creato correttamente", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Errore durante la creazione del backup", Toast.LENGTH_LONG).show();
        }
    }

    private void askRestoreMode(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("Ripristina backup")
                .setMessage("“Unisci” aggiorna i duplicati e conserva gli altri clienti. “Sostituisci” cancella prima l’archivio attuale.")
                .setPositiveButton("Unisci", (d, w) -> restoreBackup(uri, false))
                .setNeutralButton("Sostituisci", (d, w) -> confirmReplace(uri))
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void confirmReplace(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("Conferma sostituzione")
                .setMessage("Tutti i clienti attuali verranno sostituiti dal contenuto del backup.")
                .setPositiveButton("Sostituisci", (d, w) -> restoreBackup(uri, true))
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void restoreBackup(Uri uri, boolean replace) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("File non disponibile");
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) data.write(buffer, 0, count);
            int imported = db.importJson(data.toString(StandardCharsets.UTF_8.name()), replace);
            loadClients();
            Toast.makeText(this, imported + " clienti ripristinati", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Backup non valido o danneggiato", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onCall(Client client) {
        if (client.phone.isEmpty()) return;
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(client.phone))));
    }

    @Override public void onWhatsApp(Client client) {
        String number = ClientDbHelper.normalizePhone(client.phone).replace("+", "");
        if (number.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number));
        try { startActivity(intent); }
        catch (Exception e) { Toast.makeText(this, "Impossibile aprire WhatsApp", Toast.LENGTH_LONG).show(); }
    }

    @Override public void onEdit(Client client) { openEditor(client, null); }
}
