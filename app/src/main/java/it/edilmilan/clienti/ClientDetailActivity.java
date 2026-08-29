package it.edilmilan.clienti;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ClientDetailActivity extends Activity {
    public static final String EXTRA_ID = "client_id";
    private static final int REQUEST_EDIT = 40;

    private ClientDbHelper db;
    private Client client;
    private long clientId;
    private TextView name, phase, temperature, followUpBadge, pulseValue, pulseTrend, aiTemperature;
    private TextView phone, email, address, birthday, followUp, notes, emptyHistory, ge360Status;
    private ProgressBar pulseBar;
    private LinearLayout historyContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_detail);
        clientId = getIntent().getLongExtra(EXTRA_ID, 0);
        db = new ClientDbHelper(this);
        bindViews();
        wireActions();
    }

    @Override protected void onResume() {
        super.onResume();
        loadClient();
    }

    private void bindViews() {
        name = findViewById(R.id.detailName);
        phase = findViewById(R.id.detailPhase);
        temperature = findViewById(R.id.temperatureBadge);
        followUpBadge = findViewById(R.id.followUpBadge);
        pulseValue = findViewById(R.id.pulseValue);
        pulseTrend = findViewById(R.id.pulseTrend);
        aiTemperature = findViewById(R.id.aiTemperatureLabel);
        pulseBar = findViewById(R.id.pulseBar);
        phone = findViewById(R.id.detailPhone);
        email = findViewById(R.id.detailEmail);
        address = findViewById(R.id.detailAddress);
        birthday = findViewById(R.id.detailBirthday);
        followUp = findViewById(R.id.detailFollowUp);
        notes = findViewById(R.id.detailNotes);
        emptyHistory = findViewById(R.id.emptyHistory);
        historyContainer = findViewById(R.id.historyContainer);
        ge360Status = findViewById(R.id.ge360Status);
    }

    private void wireActions() {
        findViewById(R.id.detailBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.detailEditButton).setOnClickListener(v -> openEditor());
        findViewById(R.id.detailCallButton).setOnClickListener(v -> callClient());
        findViewById(R.id.detailWhatsappButton).setOnClickListener(v -> openWhatsApp());
        findViewById(R.id.detailAttrezziButton).setOnClickListener(v -> openInAttrezzi());
        findViewById(R.id.detailMessaggiButton).setOnClickListener(v -> openInMessaggi());
        bindTemperature(R.id.tempHot, "Caldo");
        bindTemperature(R.id.tempGrow, "Da coltivare");
        bindTemperature(R.id.tempCold, "Freddo");
        bindTemperature(R.id.tempDormant, "Dormiente");
        bindSignal(R.id.signalPositive, "Positivo");
        bindSignal(R.id.signalNeutral, "Neutro");
        bindSignal(R.id.signalDoubtful, "Dubbioso");
        bindSignal(R.id.signalCurious, "Curioso");
        bindSignal(R.id.signalNegative, "Negativo");
        bindSignal(R.id.signalTense, "Teso");
    }

    private void bindSignal(int buttonId, String signal) {
        findViewById(buttonId).setOnClickListener(v -> showSignalDialog(signal, RelationshipRules.deltaFor(signal)));
    }

    private void bindTemperature(int buttonId, String value) {
        findViewById(buttonId).setOnClickListener(v -> {
            db.updateTemperature(clientId, value);
            loadClient();
            Toast.makeText(this, "Temperatura aggiornata: " + value, Toast.LENGTH_SHORT).show();
        });
    }

    private void loadClient() {
        client = db.get(clientId);
        if (client == null) {
            Toast.makeText(this, "Cliente non trovato", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        name.setText(client.fullName());
        phase.setText(client.relationshipPhase);
        temperature.setText("Temperatura · " + client.temperature);
        followUpBadge.setText(client.followUp.isEmpty() ? "Nessun follow-up" : "Follow-up · " + client.followUp);
        pulseValue.setText(client.pulse + "/100");
        pulseBar.setProgress(client.pulse);
        aiTemperature.setText("IA futura · " + client.aiTemperature + " — dati pronti per analisi esterna");
        phone.setText("Telefono  ·  " + valueOrDash(client.phone));
        email.setText("Email  ·  " + valueOrDash(client.email));
        address.setText("Cantiere  ·  " + valueOrDash(client.address));
        birthday.setText("Compleanno  ·  " + valueOrDash(client.birthday));
        followUp.setText("Follow-up  ·  " + valueOrDash(client.followUp));
        notes.setText("Note  ·  " + valueOrDash(client.notes));
        ge360Status.setText(Ge360Bridge.status(this, clientId));
        Button callButton = findViewById(R.id.detailCallButton);
        Button whatsappButton = findViewById(R.id.detailWhatsappButton);
        callButton.setEnabled(!client.phone.isEmpty());
        whatsappButton.setEnabled(!client.phone.isEmpty());
        renderHistory();
    }

    private String valueOrDash(String value) {
        return Client.safe(value).isEmpty() ? "—" : Client.safe(value);
    }

    private void renderHistory() {
        List<RelationshipInteraction> history = db.getInteractions(clientId, 30);
        historyContainer.removeAllViews();
        emptyHistory.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
        if (history.isEmpty()) {
            pulseTrend.setText("Stabile · nessun segnale");
            return;
        }
        RelationshipInteraction latest = history.get(0);
        if (latest.delta > 0) pulseTrend.setText("↑ In miglioramento · ultimo " + signed(latest.delta));
        else if (latest.delta < 0) pulseTrend.setText("↓ Da seguire · ultimo " + signed(latest.delta));
        else pulseTrend.setText("→ Stabile · ultimo 0");

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.ITALY);
        for (RelationshipInteraction row : history) {
            TextView item = new TextView(this);
            item.setBackgroundResource(R.drawable.history_item);
            item.setTextColor(Color.parseColor("#171717"));
            item.setTextSize(13);
            String detailLine = row.detail.isEmpty() ? "" : "\n“" + row.detail + "”";
            item.setText(row.signal + "  " + signed(row.delta) + "  →  Polso " + row.pulseAfter +
                    "\n" + row.reason + " · " + dateFormat.format(new Date(row.createdAt)) + detailLine);
            historyContainer.addView(item);
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(1, dp(8)));
            historyContainer.addView(spacer);
        }
    }

    private String signed(int delta) {
        return delta > 0 ? "+" + delta : String.valueOf(delta);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showSignalDialog(String signal, int delta) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), 0);

        TextView prompt = new TextView(this);
        prompt.setText("Perché il contatto è stato “" + signal + "”?");
        prompt.setTextColor(Color.parseColor("#171717"));
        prompt.setTextSize(14);
        content.addView(prompt);

        Spinner reasons = new Spinner(this);
        ArrayAdapter<CharSequence> reasonAdapter = ArrayAdapter.createFromResource(this,
                R.array.relationship_reasons, android.R.layout.simple_spinner_dropdown_item);
        reasons.setAdapter(reasonAdapter);
        content.addView(reasons, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        EditText detail = new EditText(this);
        detail.setHint("Dettaglio o aneddoto facoltativo");
        detail.setMinLines(3);
        detail.setGravity(android.view.Gravity.TOP);
        content.addView(detail, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(signal + " · " + signed(delta))
                .setView(content)
                .setPositiveButton("Registra", (dialog, which) -> {
                    db.addInteraction(clientId, signal, reasons.getSelectedItem().toString(), detail.getText().toString(), delta);
                    loadClient();
                    Toast.makeText(this, "Segnale registrato nello storico", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void openEditor() {
        Intent intent = new Intent(this, EditClientActivity.class);
        intent.putExtra(EditClientActivity.EXTRA_ID, clientId);
        startActivityForResult(intent, REQUEST_EDIT);
    }

    private void callClient() {
        if (client == null || client.phone.isEmpty()) return;
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(client.phone))));
    }

    private void openWhatsApp() {
        if (client == null) return;
        String number = ClientDbHelper.normalizePhone(client.phone).replace("+", "");
        if (number.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number)));
        } catch (Exception e) {
            Toast.makeText(this, "Impossibile aprire WhatsApp", Toast.LENGTH_LONG).show();
        }
    }

    private void openInAttrezzi() {
        if (client == null) return;
        Ge360Bridge.Result result = Ge360Bridge.openInAttrezzi(this, client);
        Toast.makeText(this, result.message, result.success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        ge360Status.setText(Ge360Bridge.status(this, clientId));
    }

    private void openInMessaggi() {
        if (client == null) return;
        Ge360Bridge.Result result = Ge360MessaggiBridge.open(this, client);
        Toast.makeText(this, result.message, result.success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
    }
}
