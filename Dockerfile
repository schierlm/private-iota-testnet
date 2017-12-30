FROM openjdk:8-jdk
MAINTAINER Johannes Innerbichler <j.innerbichler@gmail.com>

COPY ./target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar /usr/src/app.jar

CMD ["--host=localhost", "--port=14265", "--interval=30"]
ENTRYPOINT ["java", "-jar", "/usr/src/app.jar", "PeriodicCoordinator"]