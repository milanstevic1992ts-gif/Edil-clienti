package it.edilmilan.clienti;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.SparseBooleanArray;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContactImportActivity extends Activity {
    public static final String EXTRA_IMPORTED = "imported_count";
    public static final String EXTRA_SKIPPED = "skipped_count";

    private final List<ContactCandidate> contacts = new ArrayList<>();
    private ListView list;
    private TextView countLabel;
    private ClientDbHelper db;
    private boolean allSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_contacts);
        db = new ClientDbHelper(this);
        list = findViewById(R.id.contactsImportList);
        countLabel = findViewById(R.id.importCountLabel);
        findViewById(R.id.importBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.selectAllContactsButton).setOnClickListener(v -> toggleAll());
        findViewById(R.id.importSelectedButton).setOnClickListener(v -> importSelected());
        new Thread(this::loadContacts).start();
    }

    private void loadContacts() {
        Map<String, ContactCandidate> unique = new LinkedHashMap<>();
        String[] projection = {ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME};
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                projection, null, null,
                ContactsContract.Contacts.DISPLAY_NAME + " COLLATE NOCASE")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(0);
                    if (!unique.containsKey(id)) {
                        ContactCandidate candidate = new ContactCandidate();
                        candidate.contactId = id;
                        candidate.displayName = Client.safe(cursor.getString(1));
                        candidate.phone = firstValue(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                ContactsContract.CommonDataKinds.Phone.NUMBER,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID, id);
                        candidate.email = firstValue(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                ContactsContract.CommonDataKinds.Email.ADDRESS,
                                ContactsContract.CommonDataKinds.Email.CONTACT_ID, id);
                        candidate.address = firstValue(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
                                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                                ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID, id);
                        if (!candidate.phone.isEmpty() || !candidate.email.isEmpty()) unique.put(id, candidate);
                    }
                }
            }
        }
        contacts.addAll(unique.values());
        List<String> labels = new ArrayList<>();
        for (ContactCandidate contact : contacts) {
            String contactLine = contact.phone.isEmpty() ? contact.email : contact.phone;
            labels.add(contact.displayName + "\n" + contactLine);
        }
        runOnUiThread(() -> {
            list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, labels));
            countLabel.setText(contacts.size() + (contacts.size() == 1 ? " contatto disponibile" : " contatti disponibili"));
        });
    }

    private void toggleAll() {
        allSelected = !allSelected;
        for (int i = 0; i < contacts.size(); i++) list.setItemChecked(i, allSelected);
        ((Button) findViewById(R.id.selectAllContactsButton)).setText(allSelected ? "Deseleziona tutti" : "Seleziona tutti");
    }

    private void importSelected() {
        SparseBooleanArray checked = list.getCheckedItemPositions();
        List<ContactCandidate> selected = new ArrayList<>();
        for (int i = 0; i < contacts.size(); i++) {
            if (checked.get(i)) selected.add(contacts.get(i));
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Seleziona almeno un contatto", Toast.LENGTH_LONG).show();
            return;
        }
        Button importButton = findViewById(R.id.importSelectedButton);
        importButton.setEnabled(false);
        importButton.setText("Importazione in corso…");
        new Thread(() -> {
            int imported = 0;
            int skipped = 0;
            for (ContactCandidate candidate : selected) {
                Client client = candidate.toClient();
                if (db.findDuplicate(client.phone, client.email) != null) skipped++;
                else {
                    db.save(client);
                    imported++;
                }
            }
            int importedResult = imported;
            int skippedResult = skipped;
            runOnUiThread(() -> {
                Intent result = new Intent();
                result.putExtra(EXTRA_IMPORTED, importedResult);
                result.putExtra(EXTRA_SKIPPED, skippedResult);
                setResult(RESULT_OK, result);
                finish();
            });
        }).start();
    }

    private String firstValue(Uri uri, String valueColumn, String idColumn, String contactId) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{valueColumn},
                idColumn + "=?", new String[]{contactId}, null)) {
            if (cursor != null && cursor.moveToFirst()) return Client.safe(cursor.getString(0));
        }
        return "";
    }

    private static class ContactCandidate {
        String contactId = "";
        String displayName = "";
        String phone = "";
        String email = "";
        String address = "";

        Client toClient() {
            Client client = new Client();
            int space = displayName.indexOf(' ');
            if (space < 0) client.firstName = displayName;
            else {
                client.firstName = displayName.substring(0, space).trim();
                client.lastName = displayName.substring(space + 1).trim();
            }
            client.phone = phone;
            client.email = email;
            client.address = address;
            client.contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId).toString();
            return client;
        }
    }
}
