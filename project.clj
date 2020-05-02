(defproject datalogger "0.1.0-SNAPSHOT"

  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [metosin/jsonista "0.2.5"]
   [org.slf4j/slf4j-api "2.0.0-alpha1"]
   [org.slf4j/log4j-over-slf4j "2.0.0-alpha1"]
   [org.slf4j/jcl-over-slf4j "2.0.0-alpha1"]
   [org.slf4j/osgi-over-slf4j "2.0.0-alpha1"]
   [org.slf4j/jul-to-slf4j "2.0.0-alpha1"]]

  :aot
  [datalogger.provider]

  :repl-options
  {:init-ns datalogger.core})
