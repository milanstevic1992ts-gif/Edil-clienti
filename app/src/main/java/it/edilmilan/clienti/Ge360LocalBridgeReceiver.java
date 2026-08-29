package it.edilmilan.clienti;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.json.JSONArray;

public class Ge360LocalBridgeReceiver extends BroadcastReceiver {
    private static final String ACTION_PING = "com.edilmilan.ge360.local.PING";
    private static final String ACTION_PONG = "com.edilmilan.ge360.local.PONG";
    private static final String PERMISSION = "com.edilmilan.ge360.permission.INTEGRATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_PING.equals(intent.getAction())) return;
        String callback = safe(intent.getStringExtra("ge360_callback_package"));
        if (callback.isEmpty()) return;
        if (context.getPackageManager().checkSignatures(context.getPackageName(), callback) != PackageManager.SIGNATURE_MATCH) return;

        JSONArray capabilities = new JSONArray()
                .put("bridge.discovery")
                .put("client.source")
                .put("client.context.publish")
                .put("jobsite.context.publish")
                .put("tools.handoff.publish")
                .put("message.compose.launch")
                .put("relationship.events")
                .put("offline.local-first");

        Intent pong = new Intent(ACTION_PONG);
        pong.setPackage(callback);
        pong.putExtra("ge360_app_id", context.getPackageName());
        pong.putExtra("ge360_module", "clienti");
        pong.putExtra("ge360_protocol_version", "2");
        pong.putExtra("ge360_capabilities_json", capabilities.toString());
        pong.putExtra("ge360_app_version", "1.2.0");
        pong.putExtra("ge360_status", "OK");
        pong.putExtra("ge360_request_id", intent.getStringExtra("ge360_request_id"));
        pong.putExtra("ge360_sent_at", System.currentTimeMillis());
        context.sendBroadcast(pong, PERMISSION);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
