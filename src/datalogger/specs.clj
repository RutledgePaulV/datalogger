(ns datalogger.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::ident (s/or :keyword keyword? :string string?))
(s/def ::level #{:trace :debug :info :warn :error})
(s/def ::pointer qualified-symbol?)
(s/def ::json-config (s/map-of keyword? any?))
(s/def ::levels (s/map-of string? ::level))
(s/def ::stream #{:stderr :stdout})
(s/def ::json-options (s/or :config ::json-config :pointer ::pointer))
(s/def ::mask (s/or :string string? :pointer ::pointer))
(s/def ::elide (s/coll-of ::ident :kind set?))
(s/def ::keys (s/coll-of ::ident :kind set?))
(s/def ::values (s/coll-of string? :kind set?))
(s/def ::masking (s/keys :opt-un [::mask ::keys ::values]))
(s/def ::root-only boolean?)
(s/def ::exceptions (s/keys :opt-un [::root-only]))
(s/def ::config (s/keys :opt-un [::json-options ::levels ::stream ::masking ::elide ::exceptions]))