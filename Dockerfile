## Stage 1 : build with maven builder image with native capabilities
FROM quay.io/quarkus/ubi-quarkus-graalvmce-builder-image:22.3-java17 AS build
COPY --chown=quarkus:quarkus mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/
USER quarkus
WORKDIR /code
RUN chmod +x ./mvnw && ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline
COPY src /code/src
ENV APP_NAME=nodetechnicalsupport
ENV QUARKUS_PROFILE=prod

RUN ./mvnw package -DskipTests=true -Dquarkus.application.name=$APP_NAME -Dquarkus.profile=$QUARKUS_PROFILE

FROM registry.access.redhat.com/ubi8/openjdk-17:1.14

ENV LANGUAGE='en_US:en'

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --from=build /code/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build /code/target/quarkus-app/*.jar /deployments/
COPY --from=build /code/target/quarkus-app/app/ /deployments/app/
COPY --from=build /code/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185

ENV APP_NAME=nodetechnicalsupport
ENV QUARKUS_PROFILE=prod

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Dquarkus.application.name=$APP_NAME -Dquarkus.profile=$QUARKUS_PROFILE -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"


