# Node tech support API

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=pagopa_pagopa-node-technical-support-worker&metric=alert_status)](https://sonarcloud.io/dashboard?id=pagopa_pagopa-node-technical-support-worker)


---
## API Documentation 📖
See the [OpenAPI 3 here.](https://editor.swagger.io/?url=https://raw.githubusercontent.com/pagopa/pagopa-node-technical-support-worker/main/openapi/openapi.json)

---
## Technology Stack
- Maven
- Java 17
- Quarkus
---

## Develop Locally 💻

### Prerequisites
- git
- maven
- jdk-11

### Setup
With skdman,in terminal:

`sdk install java 17.0.7-graal`

`sdk use java 17.0.7-graal`

or download **java 17.0.7-graal** and set as java home

### Build & Run (docker)
Build the image with

`./build-and-run.sh build`

Needed env variables:
```
COSMOS_BIZ_ENDPOINT
COSMOS_BIZ_KEY
COSMOS_NEG_BIZ_ENDPOINT
COSMOS_NEG_BIZ_KEY
RE_TABLE_STORAGE_CONN_STRING
RE_COSMOS_CONNECTION_STRING
```
Run application with

`./build-and-run.sh run`

### Generate openapi file
Generate openapi.json file in **./openapi/openapi.json**

`./build-and-run.sh generate_openapi`

### Run the project

Run in development mode with command
`quarkus dev`

### Quarkus Profiles

`dev` active in development

`test` active in tests

`openapi` active only for openapi generation

`prod` default for run

### Testing 🧪

#### Unit testing

To run the **Junit** tests:

`mvn clean verify`

---

## Contributors 👥
Made with ❤️ by PagoPa S.p.A.

### Mainteiners
See `CODEOWNERS` file
