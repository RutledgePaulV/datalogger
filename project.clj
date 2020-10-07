(defproject org.clojars.rutledgepaulv/datalogger "0.1.13-SNAPSHOT"

  :description
  "A simple structured logging library for Clojure that plays nice with others."

  :url
  "https://github.com/rutledgepaulv/datalogger"

  :license
  {:name "MIT License" :url "http://opensource.org/licenses/MIT" :year 2020 :key "mit"}

  :scm
  {:name "git" :url "https://github.com/rutledgepaulv/datalogger"}

  :pom-addition
  [:developers
   [:developer
    [:name "Paul Rutledge"]
    [:url "https://github.com/rutledgepaulv"]
    [:email "rutledgepaulv@gmail.com"]
    [:timezone "-5"]]]

  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [metosin/jsonista "0.2.7"]
   [org.slf4j/slf4j-api "2.0.0-alpha1"]
   [org.slf4j/log4j-over-slf4j "2.0.0-alpha1"]
   [org.slf4j/jcl-over-slf4j "2.0.0-alpha1"]
   [org.slf4j/osgi-over-slf4j "2.0.0-alpha1"]
   [org.slf4j/jul-to-slf4j "2.0.0-alpha1"]
   [org.clojars.rutledgepaulv/avow "0.1.0"]]

  :aot
  [datalogger.impl.provider]

  :jar-exclusions
  [#"^datalogger\.edn$"]

  :deploy-repositories
  [["releases" :clojars]
   ["snapshots" :clojars]]

  :plugins
  [[lein-cloverage "1.1.2"]]

  :repl-options
  {:init-ns datalogger.core})
