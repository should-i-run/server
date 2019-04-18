FROM clojure:openjdk-8-lein-2.9.1-alpine
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY project.clj /usr/src/app/
RUN lein deps

COPY . /usr/src/app
# RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app-standalone.jar
# CMD ["java", "-jar", "app-standalone.jar"]
# CMD ["java", "-jar", "app-standalone.jar"]
RUN lein uberjar
# CMD ["java", "-jar", "/usr/src/app/target/should-i-run.jar"]
EXPOSE 3000
CMD ["java", "-jar", "target/should-i-run.jar",  "-m", "sir.handler"]