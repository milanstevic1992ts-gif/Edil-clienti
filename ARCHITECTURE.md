# Architettura EDIL Clienti

## Flusso dell’interfaccia

1. `MainActivity` mostra ricerca, filtri e schede cliente.
2. Un tocco sulla scheda apre `ClientDetailActivity`, la dashboard operativa.
3. La dashboard separa temperatura commerciale e Polso del rapporto.
4. `EditClientActivity` modifica dati, fase, promemoria e correzioni manuali.
5. `ContactImportActivity` importa più contatti dalla rubrica Google/telefono.

## Dati locali

- `clients`: anagrafica, temperatura, fase, Polso, compleanno e follow-up;
- `relationship_interactions`: segnali, motivazioni, note e andamento storico;
- `outbox_events`: eventi offline in attesa del futuro Bridge/n8n.

Il database è SQLite locale. La migrazione dalla versione 1 alla 2 aggiunge i nuovi campi senza eliminare clienti esistenti.

## Regole del rapporto

`RelationshipRules` contiene le variazioni graduali del Polso:

- Positivo `+6`;
- Curioso `+3`;
- Neutro `0`;
- Dubbioso `-4`;
- Negativo `-8`;
- Teso `-12`.

`Client.clampPulse()` impedisce sempre valori inferiori a 0 o superiori a 100.

## Integrazione futura

Ogni segnale crea `relationship.signal.created`; ogni cambio della temperatura crea `client.temperature.changed`. Gli eventi restano offline con stato `pending`. Un futuro Bridge potrà leggerli, inviarli a n8n/IA e marcarli come completati senza modificare le schermate o la struttura dati.

Nessun servizio esterno è attivo nella versione attuale.

## Backup e ripristino

`AutoBackupJobService` usa `JobScheduler` per richiamare il backup ogni 24 ore e mantenere la pianificazione dopo il riavvio. `AutomaticBackupManager` esporta un unico JSON coerente e lo salva prima nella memoria locale dell’app, poi nella cartella autorizzata tramite Storage Access Framework (per esempio Google Drive).

Le due destinazioni sono indipendenti: un errore o l’assenza di rete per Drive non impedisce la copia locale. La rotazione ordina i file automatici per data e conserva gli ultimi 10 in ciascuna destinazione. Il backup manuale resta separato e non viene eliminato dalla rotazione automatica.
