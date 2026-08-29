# Changelog

## 1.2.0

- ripristinata temperatura commerciale: Caldo, Da coltivare, Freddo, Dormiente;
- nuova dashboard cliente separata dalla schermata di modifica;
- ripristinato Polso del rapporto 0–100 con valore iniziale 50;
- segnali rapidi Positivo, Neutro, Dubbioso, Curioso, Negativo e Teso;
- motivazioni e dettaglio facoltativo per ogni segnale;
- storico completo con variazione, valore successivo, data e andamento;
- fase del rapporto, compleanno e follow-up;
- correzione manuale del Polso registrata nello storico;
- predisposizione temperatura IA futura senza attivare servizi esterni;
- filtri rapidi Tutti, Caldi, Da coltivare, Follow-up e Polso basso;
- backup JSON aggiornato per includere tutti i nuovi dati e lo storico;
- migrazione sicura del database dalla versione 1 alla versione 2;
- test unitari per limiti del Polso e variazioni dei segnali.
- importazione multipla selettiva da rubrica Google/telefono con duplicati saltati.
- coda eventi offline per futura integrazione Bridge, n8n e IA.
- backup automatico giornaliero sul telefono e nella cartella Google Drive autorizzata;
- rotazione indipendente degli ultimi 10 backup locali e degli ultimi 10 su Drive;
- backup locale garantito anche quando Drive è offline o non configurato;
- comando di backup immediato e ripristino diretto dell’ultima copia locale.
- workflow GitHub Actions manuale per test e generazione degli APK, con firma protetta opzionale.

## 1.1.0

- nuova schermata principale più pulita e utilizzabile con una mano;
- schede cliente compatte con iniziali, contatti e azioni rapide;
- backup e ripristino spostati nel menu superiore;
- form cliente suddiviso in Dati, Contatti, Cantiere e Rubrica;
- pulsante Salva sempre visibile in fondo allo schermo;
- gerarchia visiva e stato vuoto migliorati.

## 1.0.0

- prima versione Android nativa;
- gestione completa dell’archivio clienti;
- importazione dalla rubrica Android/Google;
- salvataggio e aggiornamento contatti nella rubrica;
- rilevamento duplicati;
- collegamenti rapidi a telefono e WhatsApp;
- backup e ripristino JSON;
- tema EDIL Milan arancione e nero.
