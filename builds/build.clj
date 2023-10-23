(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'org.clojars.rutledgepaulv/datalogger)
(def version "2.0.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/datalogger.jar" (name lib) version))

(defn get-version [_]
  (print version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn compile [_]
  (b/compile-clj
    {:basis      basis
     :src-dirs   ["src"]
     :ns-compile ['datalogger.impl.provider]
     :class-dir  class-dir}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     basis
                :pom-data  [[:licenses
                             [:license
                              [:name "MIT"]
                              [:url "https://opensource.org/license/mit/"]]]]
                :src-dirs  ["src"]})
  (compile _)
  (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
  (b/jar {:class-dir class-dir :jar-file jar-file}))