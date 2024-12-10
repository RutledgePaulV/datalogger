#!/usr/bin/env bash
set -e

clj -X:build clean
clj -X:build jar
version=$(clj -X:build get-version)

export CLOJARS_USERNAME="op://ifyyzxjhwnttc3oqug3vdigdwe/clojars.org/username"
export CLOJARS_PASSWORD="op://ifyyzxjhwnttc3oqug3vdigdwe/clojars.org/token"

op run -- mvn deploy:deploy-file \
  -DgroupId="org.clojars.rutledgepaulv" \
  -DartifactId="datalogger" \
  -Dversion="$version" \
  -Dpackaging="jar" \
  -Dfile="target/datalogger.jar" \
  -DrepositoryId="clojars" \
  -Durl="https://repo.clojars.org"

git tag "v$version"
git push origin "refs/tags/v$version"
