package it.edilmilan.clienti;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class EditClientActivity extends Activity {
    public static final String EXTRA_ID = "client_id";
    public static final String EXTRA_FIRST_NAME = "prefill_first_name";
    public static final String EXTRA_LAST_NAME = "prefill_last_name";
    public static final String EXTRA_PHONE = "prefill_phone";
    public static final String EXTRA_EMAIL = "prefill_email";
    public static final String EXTRA_ADDRESS = "prefill_address";
    public static final String EXTRA_CONTACT_URI = "prefill_contact_uri";

    private ClientDbHelper db;
    private Client client;
    private EditText firstName, lastName, phone, email, address, notes;
    private CheckBox saveToContacts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_client);
        db = new ClientDbHelper(this);
        bindViews();

        long id = getIntent().getLongExtra(EXTRA_ID, 0);
        client = id > 0 ? db.get(id) : new Client();
        if (client == null) client = new Client();
        applyPrefill(getIntent());
        showClient();

        TextView title = findViewById(R.id.screenTitle);
        Button delete = findViewById(R.id.deleteButton);
        title.setText(client.id > 0 ? "Modifica cliente" : "Nuovo cliente");
        delete.setVisibility(client.id > 0 ? View.VISIBLE : View.GONE);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.saveButton).setOnClickListener(v -> save());
        delete.setOnClickListener(v -> confirmDelete());
    }

    private void bindViews() {
        firstName = findViewById(R.id.firstNameInput);
        lastName = findViewById(R.id.lastNameInput);
        phone = findViewById(R.id.phoneInput);
        email = findViewById(R.id.emailInput);
        address = findViewById(R.id.addressInput);
        notes = findViewById(R.id.notesInput);
        saveToContacts = findViewById(R.id.saveToContactsCheck);
    }

    private void applyPrefill(Intent intent) {
        if (intent.hasExtra(EXTRA_FIRST_NAME)) client.firstName = preferred(intent.getStringExtra(EXTRA_FIRST_NAME), client.firstName);
        if (intent.hasExtra(EXTRA_LAST_NAME)) client.lastName = preferred(intent.getStringExtra(EXTRA_LAST_NAME), client.lastName);
        if (intent.hasExtra(EXTRA_PHONE)) client.phone = preferred(intent.getStringExtra(EXTRA_PHONE), client.phone);
        if (intent.hasExtra(EXTRA_EMAIL)) client.email = preferred(intent.getStringExtra(EXTRA_EMAIL), client.email);
        if (intent.hasExtra(EXTRA_ADDRESS)) client.address = preferred(intent.getStringExtra(EXTRA_ADDRESS), client.address);
        if (intent.hasExtra(EXTRA_CONTACT_URI)) client.contactUri = preferred(intent.getStringExtra(EXTRA_CONTACT_URI), client.contactUri);
    }

    private String preferred(String incoming, String current) {
        return Client.safe(incoming).isEmpty() ? Client.safe(current) : Client.safe(incoming);
    }

    private void showClient() {
        firstName.setText(client.firstName);
        lastName.setText(client.lastName);
        phone.setText(client.phone);
        email.setText(client.email);
        address.setText(client.address);
        notes.setText(client.notes);
    }

    private void save() {
        client.firstName = Client.safe(firstName.getText().toString());
        client.lastName = Client.safe(lastName.getText().toString());
        client.phone = Client.safe(phone.getText().toString());
        client.email = Client.safe(email.getText().toString());
        client.address = Client.safe(address.getText().toString());
        client.notes = Client.safe(notes.getText().toString());

        if (client.fullName().isEmpty()) {
            firstName.setError("Inserisci almeno il nome del cliente");
            firstName.requestFocus();
            return;
        }
        if (client.phone.isEmpty() && client.email.isEmpty()) {
            phone.setError("Inserisci telefono o email");
            phone.requestFocus();
            return;
        }

        Client duplicate = db.findDuplicate(client.phone, client.email);
        if (duplicate != null && duplicate.id != client.id) {
            new AlertDialog.Builder(this)
                    .setTitle("Possibile duplicato")
                    .setMessage("“" + duplicate.fullName() + "” usa già lo stesso telefono o email. Vuoi salvare comunque?")
                    .setPositiveButton("Salva comunque", (d, w) -> persist())
                    .setNegativeButton("Controlla", null)
                    .show();
            return;
        }
        persist();
    }

    private void persist() {
        db.save(client);
        setResult(RESULT_OK);
        Toast.makeText(this, "Cliente salvato", Toast.LENGTH_SHORT).show();
        if (saveToContacts.isChecked()) openSystemContactEditor();
        finish();
    }

    private void openSystemContactEditor() {
        Intent intent;
        if (!Client.safe(client.contactUri).isEmpty()) {
            intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(Uri.parse(client.contactUri), ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        } else {
            intent = new Intent(ContactsContract.Intents.Insert.ACTION);
            intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);
        }
        intent.putExtra(ContactsContract.Intents.Insert.NAME, client.fullName());
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, client.phone);
        intent.putExtra(ContactsContract.Intents.Insert.EMAIL, client.email);
        intent.putExtra(ContactsContract.Intents.Insert.POSTAL, client.address);
        intent.putExtra(ContactsContract.Intents.Insert.NOTES,
                "Cliente EDIL MILAN STEVIC" + (client.notes.isEmpty() ? "" : "\n" + client.notes));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cliente salvato nell’app, ma la Rubrica non è disponibile", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Elimina cliente")
                .setMessage("Eliminare definitivamente “" + client.fullName() + "” dall’app? Il contatto nella rubrica non verrà cancellato.")
                .setPositiveButton("Elimina", (d, w) -> {
                    db.delete(client.id);
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton("Annulla", null)
                .show();
    }
}
