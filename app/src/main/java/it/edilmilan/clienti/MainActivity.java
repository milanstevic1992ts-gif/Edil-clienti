package it.edilmilan.clienti;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements ClientAdapter.Listener {
    private static final int REQUEST_EDIT = 10;
    private static final int REQUEST_IMPORT_CONTACTS = 20;
    private static final int REQUEST_CONTACT_PERMISSION = 21;
    private static final int REQUEST_BACKUP = 30;
    private static final int REQUEST_RESTORE = 31;
    private static final int REQUEST_DRIVE_FOLDER = 32;

    private ClientDbHelper db;
    private ClientAdapter adapter;
    private EditText searchInput;
    private TextView countLabel;
    private String activeFilter = "Tutti";
    private final int[] filterButtonIds = {R.id.filterAll, R.id.filterHot, R.id.filterGrow, R.id.filterFollowUp, R.id.filterRisk};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new ClientDbHelper(this);
        AutoBackupJobService.schedule(this);

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
        findViewById(R.id.filterAll).setOnClickListener(v -> setFilter("Tutti", R.id.filterAll));
        findViewById(R.id.filterHot).setOnClickListener(v -> setFilter("Caldi", R.id.filterHot));
        findViewById(R.id.filterGrow).setOnClickListener(v -> setFilter("Da coltivare", R.id.filterGrow));
        findViewById(R.id.filterFollowUp).setOnClickListener(v -> setFilter("Follow-up", R.id.filterFollowUp));
        findViewById(R.id.filterRisk).setOnClickListener(v -> setFilter("Polso basso", R.id.filterRisk));
        updateFilterAppearance(R.id.filterAll);

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
        popup.getMenu().add(0, 3, 2, "Configura cartella Google Drive");
        popup.getMenu().add(0, 4, 3, "Esegui backup automatico ora");
        popup.getMenu().add(0, 5, 4, "Ripristina ultimo backup locale");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) createBackup();
            else if (item.getItemId() == 2) chooseBackup();
            else if (item.getItemId() == 3) chooseDriveFolder();
            else if (item.getItemId() == 4) runAutomaticBackupNow();
            else if (item.getItemId() == 5) askRestoreLatestLocal();
            return true;
        });
        popup.show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) loadClients();
    }

    private void loadClients() {
        List<Client> source = db.search(searchInput == null ? "" : searchInput.getText().toString());
        List<Client> clients = new ArrayList<>();
        for (Client client : source) {
            if (matchesActiveFilter(client)) clients.add(client);
        }
        adapter.setClients(clients);
        String filterLabel = activeFilter.equals("Tutti") ? "" : " · " + activeFilter;
        countLabel.setText(clients.size() + (clients.size() == 1 ? " cliente" : " clienti") + filterLabel);
    }

    private void setFilter(String filter, int selectedButtonId) {
        activeFilter = filter;
        updateFilterAppearance(selectedButtonId);
        loadClients();
    }

    private void updateFilterAppearance(int selectedButtonId) {
        for (int id : filterButtonIds) findViewById(id).setAlpha(id == selectedButtonId ? 1f : 0.55f);
    }

    private boolean matchesActiveFilter(Client client) {
        switch (activeFilter) {
            case "Caldi": return "Caldo".equals(client.temperature);
            case "Da coltivare": return "Da coltivare".equals(client.temperature);
            case "Follow-up": return !Client.safe(client.followUp).isEmpty();
            case "Polso basso": return client.pulse < 40;
            default: return true;
        }
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

    private void openDetail(Client client) {
        Intent intent = new Intent(this, ClientDetailActivity.class);
        intent.putExtra(ClientDetailActivity.EXTRA_ID, client.id);
        startActivity(intent);
    }

    private void startContactImport() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACT_PERMISSION);
            return;
        }
        startActivityForResult(new Intent(this, ContactImportActivity.class), REQUEST_IMPORT_CONTACTS);
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
        if (requestCode == REQUEST_IMPORT_CONTACTS) {
            int imported = data.getIntExtra(ContactImportActivity.EXTRA_IMPORTED, 0);
            int skipped = data.getIntExtra(ContactImportActivity.EXTRA_SKIPPED, 0);
            loadClients();
            Toast.makeText(this, imported + " importati · " + skipped + " duplicati saltati", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_BACKUP && data.getData() != null) {
            writeBackup(data.getData());
        } else if (requestCode == REQUEST_RESTORE && data.getData() != null) {
            askRestoreMode(data.getData());
        } else if (requestCode == REQUEST_DRIVE_FOLDER && data.getData() != null) {
            Uri folder = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(folder, flags);
                AutomaticBackupManager.configureDriveFolder(this, folder);
                runAutomaticBackupNow();
            } catch (Exception e) {
                Toast.makeText(this, "Impossibile mantenere l'accesso alla cartella Drive", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void chooseDriveFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_DRIVE_FOLDER);
    }

    private void runAutomaticBackupNow() {
        Toast.makeText(this, "Backup in corso…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            AutomaticBackupManager.Result result = AutomaticBackupManager.perform(getApplicationContext());
            runOnUiThread(() -> Toast.makeText(this, result.summary(), Toast.LENGTH_LONG).show());
        }, "edil-manual-auto-backup").start();
    }

    private void askRestoreLatestLocal() {
        File backup = AutomaticBackupManager.latestLocalBackup(this);
        if (backup == null) {
            Toast.makeText(this, "Nessun backup automatico locale disponibile", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Ripristina backup locale")
                .setMessage("Vuoi unire l'ultimo backup locale con l'archivio attuale?")
                .setPositiveButton("Unisci", (dialog, which) -> restoreLocalBackup(backup, false))
                .setNeutralButton("Sostituisci", (dialog, which) -> confirmReplaceLocal(backup))
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void confirmReplaceLocal(File backup) {
        new AlertDialog.Builder(this)
                .setTitle("Conferma sostituzione")
                .setMessage("Tutti i clienti attuali verranno sostituiti dall'ultimo backup locale.")
                .setPositiveButton("Sostituisci", (dialog, which) -> restoreLocalBackup(backup, true))
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void restoreLocalBackup(File backup, boolean replace) {
        try (InputStream in = new FileInputStream(backup)) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) data.write(buffer, 0, count);
            int imported = db.importJson(data.toString(StandardCharsets.UTF_8.name()), replace);
            loadClients();
            Toast.makeText(this, imported + " clienti ripristinati dal telefono", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Backup locale non valido o danneggiato", Toast.LENGTH_LONG).show();
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

    @Override public void onOpen(Client client) { openDetail(client); }

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
