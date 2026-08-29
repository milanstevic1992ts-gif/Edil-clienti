package it.edilmilan.clienti;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class ClientAdapter extends BaseAdapter {
    public interface Listener {
        void onOpen(Client client);
        void onCall(Client client);
        void onWhatsApp(Client client);
        void onEdit(Client client);
    }

    private final LayoutInflater inflater;
    private final Listener listener;
    private List<Client> clients = new ArrayList<>();

    public ClientAdapter(Context context, Listener listener) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setClients(List<Client> clients) {
        this.clients = clients;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return clients.size(); }
    @Override public Client getItem(int position) { return clients.get(position); }
    @Override public long getItemId(int position) { return clients.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.row_client, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        Client client = getItem(position);
        holder.name.setText(client.fullName().isEmpty() ? "Cliente senza nome" : client.fullName());
        holder.initials.setText(initials(client));
        holder.phone.setText(client.phone.isEmpty() ? "Nessun telefono" : client.phone);
        String details = client.email;
        if (!client.address.isEmpty()) details += (details.isEmpty() ? "" : "\n") + client.address;
        holder.details.setText(details);
        holder.details.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);
        holder.temperature.setText(client.temperature);
        holder.phase.setText(client.relationshipPhase);
        holder.pulse.setText("Polso " + client.pulse);
        holder.call.setEnabled(!client.phone.isEmpty());
        holder.whatsapp.setEnabled(!client.phone.isEmpty());
        holder.call.setOnClickListener(v -> listener.onCall(client));
        holder.whatsapp.setOnClickListener(v -> listener.onWhatsApp(client));
        holder.edit.setOnClickListener(v -> listener.onEdit(client));
        convertView.setOnClickListener(v -> listener.onOpen(client));
        return convertView;
    }

    private String initials(Client client) {
        StringBuilder value = new StringBuilder();
        if (!client.firstName.isEmpty()) value.append(Character.toUpperCase(client.firstName.charAt(0)));
        if (!client.lastName.isEmpty()) value.append(Character.toUpperCase(client.lastName.charAt(0)));
        return value.length() == 0 ? "?" : value.toString();
    }

    private static class ViewHolder {
        final TextView initials, name, phone, details, temperature, phase, pulse;
        final Button call, whatsapp, edit;
        ViewHolder(View view) {
            initials = view.findViewById(R.id.clientInitials);
            name = view.findViewById(R.id.clientName);
            phone = view.findViewById(R.id.clientPhone);
            details = view.findViewById(R.id.clientDetails);
            temperature = view.findViewById(R.id.clientTemperature);
            phase = view.findViewById(R.id.clientPhase);
            pulse = view.findViewById(R.id.clientPulse);
            call = view.findViewById(R.id.callButton);
            whatsapp = view.findViewById(R.id.whatsappButton);
            edit = view.findViewById(R.id.editButton);
        }
    }
}
