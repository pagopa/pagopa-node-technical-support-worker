# Design: DB migration with Liquibase

## Obiettivo

Aggiornare `.github/workflows/05_db_migration_with_github_runner.yml` per applicare manualmente le migrazioni Liquibase al database di observability in `dev`, `uat` o `prod`. Ogni esecuzione deve interessare un solo ambiente selezionato e mantenere le protezioni GitHub Environment già associate a quell'ambiente.

## Comportamento

- Conservare `workflow_dispatch` con scelta obbligatoria dell'ambiente (`dev`, `uat`, `prod`) per eseguire una migrazione manuale in un solo ambiente per run.
- Validare l'ambiente prima di avviare il job di migrazione. Il job deve usare solo l'output convalidato per selezionare runner ed Environment GitHub; valori non supportati devono fallire prima di selezionare un ambiente dinamico.
- Rimuovere l'input schema e tutti i passaggi Flyway: nel repository è presente un solo changelog pertinente, `src/main/resources/db/observability/db.changelog-master.yaml`.
- Eseguire Liquibase `update` sullo schema `observability`, usando le credenziali specifiche `OBSERVABILITY_DATASOURCE_URL`, `OBSERVABILITY_DATASOURCE_USERNAME` e `OBSERVABILITY_DATASOURCE_PASSWORD`, fornite come secrets dell'environment GitHub selezionato.
- Rimuovere il trigger `workflow_call`: le credenziali devono provenire direttamente dai secrets dell'Environment GitHub selezionato, senza duplicarle o passarle da un workflow chiamante.
- Non eseguire `00-create-schema-and-user.sql`: schema e ruolo sono prerequisiti già predisposti.
- Usare Liquibase Maven con versione fissata e driver PostgreSQL esplicito nel classpath del plugin, allineandone la versione a quella già gestita dal progetto.

## Esecuzione e gestione errori

Il job continua a girare sul runner self-hosted dell'ambiente scelto e ad usare l'environment GitHub corrispondente. Il changelog viene eseguito dal percorso `db/observability/db.changelog-master.yaml` nella directory delle risorse Maven, con `defaultSchemaName=observability`. L'update deve fallire esplicitamente se Liquibase, il driver, le credenziali o il database non sono disponibili; non sono previsti fallback su Flyway o su altri datasource.

## Verifica

Validare la sintassi YAML e i riferimenti agli input/secrets; verificare il goal Liquibase Maven effettivo e che carichi il driver PostgreSQL esplicitamente dichiarato tra le dipendenze del plugin, senza connettersi ai database di ambiente. Eseguire i controlli Maven pertinenti disponibili localmente. La verifica end-to-end delle credenziali e dell'applicazione delle modifiche richiede accesso ai database `dev`, `uat` e `prod` e non va effettuata come parte dei test locali.
