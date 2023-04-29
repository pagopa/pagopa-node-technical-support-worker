# Template for Quarkus Microservice project

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=TODO-set-your-id&metric=alert_status)](https://sonarcloud.io/dashboard?id=TODO-set-your-id)

## TODO List
- Add a description 
- Generate an index with this tool: https://ecotrust-canada.github.io/markdown-toc/
- Find and solve all the TODOs in this template (e.g. in `.github` folder, `pom.xml` and so on)

---
## API Documentation 📖
See the [OpenAPI 3 here.](https://raw.githubusercontent.com/pagopa/pagopa-quarkus-template/openapi/openapi.json)

---

## Technology Stack
- Java 11
- Quarkus
- other...
---

## Running the infrastructure 🚀

### Prerequisites
- docker
- docker-compose

### Run docker container
The docker compose runs:
- ELK
    - elasticsearch
    - logstash
    - [kibana](http://localhost:5601/)
- Monitoring
    - alertmanager
    - [prometheus](http://localhost:9090/),
    - [grafana](http://localhost:3000/) (user: ```admin```, password: ```admin```)
- Tracing
    - otel-collector
    - [jaeger](http://localhost:16686/)


To run locally, from the main directory, execute
`sh run-local-infra.sh <project-name>`

From `./docker` directory
`sh ./run_docker.sh local|dev|uat|prod`

ℹ️ Note: for PagoPa ACR is required the login `az acr login -n <acr-name>`

---

## Develop Locally 💻

### Prerequisites
- git
- maven
- jdk-11

### Run the project

Start the springboot application with this command:

`mvn spring-boot:run -Dspring-boot.run.profiles=local`



### Spring Profiles

- **local**: to develop locally.
- _default (no profile set)_: The application gets the properties from the environment (for Azure).


### Testing 🧪

#### Unit testing

To run the **Junit** tests:

`mvn clean verify`

#### Integration testing
Add integration test in `.integration-test` and write here how to execute them

#### Performance testing
Add performance test using [k6](https://k6.io/) in `.performance-test` and write here how to execute them (e.g. `k6 run --env VARS=local.environment.json --env TEST_TYPE=./test-types/load.json main_scenario.js`)


---

## Contributors 👥
Made with ❤️ by PagoPa S.p.A.

### Mainteiners
See `CODEOWNERS` file
