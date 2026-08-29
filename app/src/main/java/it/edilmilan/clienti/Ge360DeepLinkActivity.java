package it.edilmilan.clienti;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class Ge360DeepLinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        if (uri == null || !"ge360".equalsIgnoreCase(uri.getScheme()) || !"clienti".equalsIgnoreCase(uri.getHost())) {
            openMain();
            return;
        }

        long localClientId = parseLong(uri.getQueryParameter("localClientId"));
        String universalClientId = safe(uri.getQueryParameter("clientId"));
        ClientDbHelper db = new ClientDbHelper(this);
        Client client = localClientId > 0 ? db.get(localClientId) : db.getByGe360Id(universalClientId);
        if (client == null) {
            openMain();
            return;
        }

        if ("/jobsite-report".equals(uri.getPath())) receiveJobsiteReport(client.id, uri);
        openClient(client.id);
    }

    private void receiveJobsiteReport(long clientId, Uri uri) {
        String encoded = safe(uri.getQueryParameter("payload"));
        String jobsiteId = safe(uri.getQueryParameter("jobsiteId"));
        if (encoded.isEmpty()) return;
        try {
            String json = decodeBase64Url(encoded);
            JSONObject report = new JSONObject(json);
            String signature = jobsiteId + "|" + encoded;
            if (!signature.equals(Ge360Bridge.lastReportSignature(this, clientId))) {
                String summary = summarize(report);
                Ge360Bridge.rememberReport(this, clientId, json, summary);
                Ge360Bridge.rememberReportSignature(this, clientId, signature);
                ClientDbHelper db = new ClientDbHelper(this);
                if (db.get(clientId) != null) {
                    db.addInteraction(clientId, "Attrezzi", "Aggiornamento cantiere GE360", summary, 0);
                }
                Toast.makeText(this, "Aggiornamento Attrezzi ricevuto", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {
            Toast.makeText(this, "Report Attrezzi non leggibile", Toast.LENGTH_LONG).show();
        }
    }

    private String summarize(JSONObject report) {
        int tools = report.optInt("toolsCount", 0);
        int loaded = report.optInt("loadedToolsCount", 0);
        int materials = report.optInt("materialsCount", 0);
        int toBuy = report.optInt("materialsToBuyCount", 0);
        int requests = report.optInt("teamRequestsOpen", 0);
        int reminders = report.optInt("remindersOpen", 0);
        String status = safe(report.optString("preparationStatus", ""));
        StringBuilder text = new StringBuilder();
        text.append("Attrezzi ").append(loaded).append('/').append(tools)
                .append(" · Materiali ").append(materials)
                .append(" · Da comprare ").append(toBuy);
        if (requests > 0) text.append(" · Richieste squadra ").append(requests);
        if (reminders > 0) text.append(" · Promemoria ").append(reminders);
        if (!status.isEmpty()) text.append(" · ").append(status);
        return text.toString();
    }

    private String decodeBase64Url(String value) {
        byte[] bytes = Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void openClient(long clientId) {
        Intent next = new Intent(this, ClientDetailActivity.class);
        next.putExtra(ClientDetailActivity.EXTRA_ID, clientId);
        next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(next);
        finish();
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private long parseLong(String value) {
        try { return Long.parseLong(safe(value)); } catch (Exception ignored) { return 0; }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
