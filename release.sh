#!/usr/bin/env bash

clj -X:build clean
clj -X:build jar

export CLOJARS_USERNAME="op://Personal/clojars.org/username"
export CLOJARS_PASSWORD="op://Personal/clojars.org/token"

op run -- mvn deploy:deploy-file \
  -DgroupId="org.clojars.rutledgepaulv" \
  -DartifactId="datalogger" \
  -Dversion="$(clj -X:build get-version)" \
  -Dpackaging="jar" \
  -Dfile="target/datalogger.jar" \
  -DrepositoryId="clojars" \
  -Durl="https://repo.clojars.org"
