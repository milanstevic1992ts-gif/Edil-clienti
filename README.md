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
- backup JSON e ripristino con modalità **Unisci** o **Sostituisci**.

I dati dell’app sono conservati in un database SQLite privato sul dispositivo. Il permesso di lettura della rubrica viene richiesto soltanto quando si usa **Importa rubrica**.

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

In questa versione non esiste alcun file in `.github/workflows`: nessuna Action viene creata o avviata. La compilazione automatica verrà aggiunta soltanto su richiesta esplicita.

## Struttura

```text
app/src/main/java/it/edilmilan/clienti/
├── MainActivity.java          # elenco, ricerca, rubrica e backup
├── EditClientActivity.java    # scheda cliente e salvataggio contatto
├── ClientDbHelper.java        # database SQLite e JSON
├── ClientAdapter.java         # schede elenco
└── Client.java                # modello dati
```

## Privacy

L’app non invia clienti o rubrica a server esterni. WhatsApp viene aperto soltanto quando l’utente preme il relativo pulsante. Il backup JSON viene scritto esclusivamente nel percorso scelto dall’utente tramite il selettore file Android.
