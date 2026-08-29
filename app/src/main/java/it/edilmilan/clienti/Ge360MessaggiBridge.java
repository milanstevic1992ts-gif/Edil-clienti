package it.edilmilan.clienti;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.util.UUID;

public final class Ge360MessaggiBridge {
    public static final String MESSAGGI_PACKAGE = "com.ge360.messaggi";

    private Ge360MessaggiBridge() { }

    public static Ge360Bridge.Result open(Activity activity, Client client) {
        if (activity == null || client == null || Client.safe(client.ge360Id).isEmpty()) {
            return new Ge360Bridge.Result(false, "none", "Identità GE360 del cliente non disponibile");
        }
        try {
            Uri uri = Uri.parse("ge360://messaggi/compose").buildUpon()
                    .appendQueryParameter("clientId", client.ge360Id)
                    .appendQueryParameter("jobsiteId", Client.safe(client.ge360JobsiteId))
                    .appendQueryParameter("name", client.fullName())
                    .appendQueryParameter("phone", Client.safe(client.phone))
                    .appendQueryParameter("address", Client.safe(client.address))
                    .appendQueryParameter("correlationId", UUID.randomUUID().toString())
                    .build();
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage(MESSAGGI_PACKAGE);
            activity.startActivity(intent);
            return new Ge360Bridge.Result(true, "deep-link", "Messaggi aperta con il contesto del cliente");
        } catch (Exception error) {
            return new Ge360Bridge.Result(false, "none", "GE360 Messaggi non disponibile");
        }
    }
}
