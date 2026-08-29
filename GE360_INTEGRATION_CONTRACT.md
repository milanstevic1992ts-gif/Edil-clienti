# GE360 Integration Contract — EDIL Clienti

EDIL Clienti è la **source of truth del cliente** nell'ecosistema GE360.

## Identità

- `clientId`: UUID stabile persistito nel database Clienti (`ge360_id`).
- `jobsiteId`: UUID stabile del cantiere principale associato al cliente (`ge360_jobsite_id`).
- `localClientId`: ID SQLite locale, valido solo dentro EDIL Clienti e mai usato come identità tra app.

Gli UUID vengono creati una sola volta, migrati per i record esistenti e inclusi nei backup JSON.

## Capacità

- pubblicazione contesto cliente/cantiere;
- handoff verso Attrezzi;
- apertura contestuale di GE360 Messaggi;
- ricezione report di ritorno da Attrezzi;
- eventi locali di temperatura commerciale e rapporto;
- discovery tramite GE360 Local Bridge Protocol 2.

## Deep link

- callback Clienti: `ge360://clienti/jobsite-report?clientId=<uuid>&localClientId=<locale>`
- Messaggi: `ge360://messaggi/compose?clientId=<uuid>&jobsiteId=<uuid>&name=...`
- Attrezzi: `ge360://attrezzi/open?...`

## Eventi prodotti

- `client.temperature.changed`
- `relationship.signal.created`

Gli eventi usano `clientId` e `jobsiteId` universali; `localClientId` può comparire solo come metadato diagnostico.

## Regole

1. Nessuna app deve identificare un cliente tramite nome, telefono o ID SQLite.
2. Ogni app conserva il proprio database locale ma scambia gli stessi UUID GE360.
3. Il trasporto locale firmato usa `com.edilmilan.ge360.permission.INTEGRATION` quando le APK condividono il certificato GE360.
4. I deep link restano disponibili come handoff esplicito e validato.
5. Offline-first: l'assenza del cervello centrale non deve bloccare il lavoro locale.

Protocollo locale: GE360 Local Bridge v2.
