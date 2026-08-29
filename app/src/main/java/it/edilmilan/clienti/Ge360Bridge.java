package it.edilmilan.clienti;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public final class Ge360Bridge {
    public static final String PERMISSION = "com.edilmilan.ge360.permission.INTEGRATION";
    public static final String ATTREZZI_PACKAGE = "com.edilmilanstevic.attrezzi";
    public static final String ACTION_HANDOFF = "com.edilmilan.ge360.action.JOBSITE_HANDOFF";
    private static final String PREFS = "ge360_bridge";

    private Ge360Bridge() { }

    public static Result openInAttrezzi(Activity activity, Client client) {
        if (activity == null || client == null || client.id <= 0) {
            return new Result(false, "none", "Cliente non valido");
        }
        try {
            String jobsiteId = jobsiteId(client.id);
            String requestId = UUID.randomUUID().toString();
            String eventId = "evt-clienti-handoff-" + client.id + "-" + System.currentTimeMillis();
            String payload = buildPayload(client, jobsiteId, eventId, requestId).toString();
            String callback = "ge360://clienti/jobsite-report?clientId=" + client.id;
            String encoded = Base64.encodeToString(payload.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            Uri uri = Uri.parse("ge360://attrezzi/open").buildUpon()
                    .appendQueryParameter("payload", encoded)
                    .appendQueryParameter("jobsiteId", jobsiteId)
                    .appendQueryParameter("clientId", String.valueOf(client.id))
                    .appendQueryParameter("callback", callback)
                    .appendQueryParameter("reportProtocol", "2")
                    .build();
            Intent direct = new Intent(Intent.ACTION_VIEW, uri);
            direct.setPackage(ATTREZZI_PACKAGE);

            if (sameSignature(activity, ATTREZZI_PACKAGE)) {
                Intent handoff = new Intent(ACTION_HANDOFF);
                handoff.setPackage(ATTREZZI_PACKAGE);
                handoff.putExtra("ge360_payload_json", payload);
                handoff.putExtra("ge360_jobsite_id", jobsiteId);
                handoff.putExtra("ge360_estimate_id", "");
                handoff.putExtra("ge360_client_id", String.valueOf(client.id));
                handoff.putExtra("ge360_callback_uri", callback);
                handoff.putExtra("ge360_callback_package", activity.getPackageName());
                handoff.putExtra("ge360_event_id", eventId);
                handoff.putExtra("ge360_request_id", requestId);
                handoff.putExtra("ge360_idempotency_key", "clienti:" + jobsiteId + ":" + client.updatedAt);
                handoff.putExtra("ge360_protocol_version", "2");
                handoff.putExtra("ge360_sender_will_launch", true);
                activity.sendBroadcast(handoff, PERMISSION);
                activity.startActivity(direct);
                rememberSent(activity, client.id, jobsiteId, "signed-broadcast+deep-link");
                return new Result(true, "signed-broadcast+deep-link", "Attrezzi aperta con il cantiere del cliente");
            }

            activity.startActivity(direct);
            rememberSent(activity, client.id, jobsiteId, "deep-link");
            return new Result(true, "deep-link", "Attrezzi aperta con il cantiere del cliente");
        } catch (Exception error) {
            return new Result(false, "none", "Attrezzi non disponibile: " + safeMessage(error));
        }
    }

    public static String status(Context context, long clientId) {
        long reportAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("client_" + clientId + "_last_report_at", 0);
        String summary = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("client_" + clientId + "_last_report_summary", "");
        if (reportAt > 0) {
            return "Ultimo ritorno da Attrezzi · " + formatTime(reportAt) + (summary.isEmpty() ? "" : "\n" + summary);
        }
        long ackAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("client_" + clientId + "_last_ack_at", 0);
        if (ackAt > 0) return "Attrezzi collegata · consegna confermata " + formatTime(ackAt);
        long sentAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("client_" + clientId + "_last_sent_at", 0);
        if (sentAt > 0) return "Ultimo invio ad Attrezzi · " + formatTime(sentAt);
        return "Nessun collegamento con Attrezzi ancora eseguito";
    }

    static void rememberAck(Context context, String jobsiteId) {
        long clientId = clientIdFromJobsite(jobsiteId);
        if (clientId <= 0) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("client_" + clientId + "_last_ack_at", System.currentTimeMillis())
                .apply();
    }

    static void rememberReport(Context context, long clientId, String payloadJson, String summary) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("client_" + clientId + "_last_report_at", System.currentTimeMillis())
                .putString("client_" + clientId + "_last_report_json", payloadJson == null ? "" : payloadJson)
                .putString("client_" + clientId + "_last_report_summary", summary == null ? "" : summary)
                .apply();
    }

    static String lastReportSignature(Context context, long clientId) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("client_" + clientId + "_last_report_signature", "");
    }

    static void rememberReportSignature(Context context, long clientId, String signature) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("client_" + clientId + "_last_report_signature", signature == null ? "" : signature)
                .apply();
    }

    private static JSONObject buildPayload(Client client, String jobsiteId, String eventId, String requestId) throws Exception {
        JSONObject root = new JSONObject();
        root.put("protocolVersion", 2);
        root.put("jobsiteId", jobsiteId);
        root.put("eventId", eventId);
        root.put("requestId", requestId);

        JSONObject source = new JSONObject();
        source.put("app", "clienti");
        source.put("module", "crm");
        source.put("clientId", String.valueOf(client.id));
        root.put("source", source);

        JSONObject site = new JSONObject();
        site.put("title", client.fullName().isEmpty() ? "Cliente " + client.id : client.fullName());
        site.put("address", Client.safe(client.address));
        site.put("city", "");
        root.put("site", site);

        JSONObject customer = new JSONObject();
        customer.put("id", String.valueOf(client.id));
        customer.put("name", client.fullName());
        customer.put("phone", Client.safe(client.phone));
        customer.put("email", Client.safe(client.email));
        customer.put("temperature", Client.safe(client.temperature));
        customer.put("pulse", client.pulse);
        root.put("client", customer);

        JSONObject commercial = new JSONObject();
        commercial.put("relationshipPhase", Client.safe(client.relationshipPhase));
        commercial.put("temperature", Client.safe(client.temperature));
        commercial.put("followUp", Client.safe(client.followUp));
        root.put("commercial", commercial);

        root.put("rooms", new JSONArray());
        root.put("workItems", new JSONArray());
        root.put("notes", Client.safe(client.notes));
        root.put("sentAt", System.currentTimeMillis());
        return root;
    }

    private static boolean sameSignature(Context context, String targetPackage) {
        try {
            return context.getPackageManager().checkSignatures(context.getPackageName(), targetPackage) == PackageManager.SIGNATURE_MATCH;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void rememberSent(Context context, long clientId, String jobsiteId, String transport) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("client_" + clientId + "_last_sent_at", System.currentTimeMillis())
                .putString("client_" + clientId + "_jobsite_id", jobsiteId)
                .putString("client_" + clientId + "_transport", transport)
                .apply();
    }

    private static String jobsiteId(long clientId) {
        return "clienti-" + clientId + "-main";
    }

    private static long clientIdFromJobsite(String jobsiteId) {
        if (jobsiteId == null || !jobsiteId.startsWith("clienti-") || !jobsiteId.endsWith("-main")) return 0;
        try {
            return Long.parseLong(jobsiteId.substring("clienti-".length(), jobsiteId.length() - "-main".length()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String formatTime(long value) {
        return new SimpleDateFormat("dd/MM HH:mm", Locale.ITALY).format(new Date(value));
    }

    private static String safeMessage(Exception error) {
        String value = error == null ? "errore sconosciuto" : error.getMessage();
        return value == null || value.trim().isEmpty() ? "app non installata o non raggiungibile" : value.trim();
    }

    public static final class Result {
        public final boolean success;
        public final String transport;
        public final String message;

        Result(boolean success, String transport, String message) {
            this.success = success;
            this.transport = transport;
            this.message = message;
        }
    }
}
