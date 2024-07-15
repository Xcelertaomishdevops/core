FROM 979668027692.dkr.ecr.ap-south-1.amazonaws.com/maven:3.9.5-openjdk-21-slim as builder
# Copy local code to the container image.
RUN mkdir -p /root/.m2 \
    && mkdir /root/.m2/repository
WORKDIR /app
COPY settings.xml /root/.m2
COPY . .
COPY ./msttcore.zip /app
WORKDIR /app/ctrm
ARG CODEARTIFACT_AUTH_TOKEN
ARG DOMAIN
ARG REPO
ARG REPO_URL
ENV CODEARTIFACT_AUTH_TOKEN=${CODEARTIFACT_AUTH_TOKEN}
ENV DOMAIN=${DOMAIN}
ENV REPO=${REPO}
ENV REPO_URL=${REPO_URL}
RUN mvn package -DskipTests
# 
FROM 979668027692.dkr.ecr.ap-south-1.amazonaws.com/openjdk:21-jdk-slim
# Copy the jar to the production image from the builder stage

COPY --from=builder /app/ctrm/launcher/target/launcher-*.jar /launcher.jar
# Run the web service on container startup.
CMD ["java","-Dliquibase.secureParsing=false", "-Djava.security.egd=file:/dev/./urandom", "-Djava.awt.headless=true", "-jar","/launcher.jar"]

#-Dspring.profiles.active=dev
