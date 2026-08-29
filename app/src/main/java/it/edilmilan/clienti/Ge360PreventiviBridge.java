package it.edilmilan.clienti;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

public final class Ge360PreventiviBridge {
    public static final String PREVENTIVI_PACKAGE = "com.edilmilanstevic.preventivi";

    private Ge360PreventiviBridge() { }

    public static Ge360Bridge.Result open(Activity activity, Client client) {
        if (activity == null || client == null || Client.safe(client.ge360Id).isEmpty()) {
            return new Ge360Bridge.Result(false, "none", "Identità GE360 del cliente non disponibile");
        }
        try {
            Uri uri = Uri.parse("ge360://estimate/new").buildUpon()
                    .appendQueryParameter("clientId", client.ge360Id)
                    .appendQueryParameter("name", client.fullName())
                    .appendQueryParameter("phone", Client.safe(client.phone))
                    .appendQueryParameter("email", Client.safe(client.email))
                    .appendQueryParameter("address", Client.safe(client.address))
                    .appendQueryParameter("source", "clienti")
                    .build();
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage(PREVENTIVI_PACKAGE);
            activity.startActivity(intent);
            return new Ge360Bridge.Result(true, "deep-link", "Preventivi aperta con il cliente collegato");
        } catch (Exception error) {
            return new Ge360Bridge.Result(false, "none", "EDIL Preventivi non disponibile");
        }
    }
}
