# EDIL Clienti

App Android nativa per la gestione clienti di **EDIL MILAN STEVIC — Restauri & Costruzioni — Trieste e provincia**.

## Funzioni operative

- archivio clienti locale e utilizzabile offline;
- ricerca per nome, cognome, telefono, email, indirizzo e note;
- inserimento, modifica ed eliminazione clienti;
- chiamata diretta e apertura chat WhatsApp;
- importazione selettiva dalla rubrica Android;
- importazione dei contatti locali e dei contatti Google sincronizzati sul telefono;
- controllo duplicati tramite telefono normalizzato ed email;
- salvataggio nella rubrica con scheda già compilata e scelta dell’account Google/Telefono;
- aggiornamento del contatto originario quando il cliente è stato importato dalla rubrica;
- backup JSON manuale e ripristino con modalità **Unisci** o **Sostituisci**;
- backup automatico giornaliero sul telefono, con conservazione degli ultimi 10 file;
- seconda copia automatica nella cartella Google Drive scelta dall’utente, anch’essa limitata agli ultimi 10 file;
- ripristino diretto dell’ultimo backup locale e ripristino dei file Drive dal selettore Android;
- layout ottimizzato per smartphone con schede compatte, iniziali cliente e pulsante Salva sempre visibile.
- temperatura commerciale manuale separata dal Polso del rapporto;
- Polso 0–100 con sei segnali rapidi, motivazioni, note e storico;
- fase cliente, compleanno, follow-up e filtri operativi;
- campi predisposti per analisi IA/n8n futura, senza dipendenze esterne attive.
- importazione multipla con selezione dalla rubrica Google/telefono;
- coda eventi offline pronta per Bridge e automazioni future.

I dati dell’app sono conservati in un database SQLite privato sul dispositivo. Il permesso di lettura della rubrica viene richiesto soltanto quando si usa **Importa rubrica**.

## Backup automatici

Dal menu in alto si può scegliere **Configura cartella Google Drive** e selezionare una cartella del proprio Drive. Android conserva l’autorizzazione alla cartella senza salvare password Google nell’app. **Esegui backup automatico ora** crea subito entrambe le copie; in seguito Android pianifica il backup ogni 24 ore e dopo i riavvii.

La copia sul telefono viene creata anche senza rete o senza Drive configurato. Al superamento di 10 file, per ciascuna destinazione viene eliminato automaticamente il più vecchio. Se Drive è temporaneamente offline, il backup locale continua comunque a essere creato.

## Requisiti di compilazione

- Android Studio con JDK 17;
- Android SDK 35;
- connessione internet solo durante il primo download delle dipendenze Gradle.

## Creare l’APK da Android Studio

1. Scaricare o clonare questa repository.
2. Aprire la cartella `Edil-clienti` con Android Studio.
3. Attendere la sincronizzazione Gradle.
4. Selezionare **Build → Build APK(s)**.
5. L’APK debug si troverà in `app/build/outputs/apk/debug/app-debug.apk`.

## Creare l’APK da terminale

Linux/macOS:

```bash
chmod +x gradlew
./gradlew assembleDebug
```

Windows:

```bat
gradlew.bat assembleDebug
```

## APK firmato di produzione

Per gli aggiornamenti futuri utilizzare sempre lo stesso keystore. Keystore, password e `keystore.properties` non devono essere caricati nella repository.

La configurazione di firma non è inclusa intenzionalmente: va impostata localmente in Android Studio tramite **Build → Generate Signed Bundle / APK**.

## GitHub Actions

Il workflow manuale `.github/workflows/build-apk.yml` esegue i test e genera l’APK debug e la release non firmata. Se sono configurati i quattro segreti protetti `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEY_ALIAS`, `SIGNING_STORE_PASSWORD` e `SIGNING_KEY_PASSWORD`, genera anche la release firmata.

La Action si avvia esclusivamente dal pulsante **Run workflow**: non parte automaticamente a ogni modifica e quindi non consuma minuti senza un comando esplicito.

## Struttura

```text
app/src/main/java/it/edilmilan/clienti/
├── MainActivity.java          # elenco, ricerca, rubrica e comandi backup
├── AutomaticBackupManager.java # copie telefono/Drive e rotazione ultimi 10
├── AutoBackupJobService.java  # pianificazione giornaliera Android
├── EditClientActivity.java    # scheda cliente e salvataggio contatto
├── ClientDbHelper.java        # database SQLite e JSON
├── ClientAdapter.java         # schede elenco
└── Client.java                # modello dati
```

## Privacy

L’app non invia clienti o rubrica a server propri. WhatsApp viene aperto soltanto quando l’utente preme il relativo pulsante. I dati arrivano a Google Drive solo dopo che l’utente ha scelto esplicitamente una cartella tramite il selettore documenti Android.
