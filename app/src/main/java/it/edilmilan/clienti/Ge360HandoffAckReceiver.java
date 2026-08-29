package it.edilmilan.clienti;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class Ge360HandoffAckReceiver extends BroadcastReceiver {
    public static final String ACTION_ACK = "com.edilmilan.ge360.action.JOBSITE_HANDOFF_ACK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_ACK.equals(intent.getAction())) return;
        String status = intent.getStringExtra("ge360_ack_status");
        if (status != null && !status.trim().isEmpty() && !"DELIVERED".equalsIgnoreCase(status.trim())) return;
        Ge360Bridge.rememberAck(context, intent.getStringExtra("ge360_jobsite_id"));
    }
}
