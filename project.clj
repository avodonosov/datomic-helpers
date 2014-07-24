;;;; -*- mode: clojure; indent-tabs-mode: nil; coding: utf-8; show-trailing-whitespace: t -*-

(defproject datomic-helpers "1.0.0"
  :description "Convenience functions to populate Datomic DB with data, to define DB schema."
  :url "https://github.com/avodonosov/datomic-helpers"
  :license {:name "MIT License" :url "file://./LICENSE"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :target-path "target/%s"
  :uberjar-name "datomic-helpers-standalone.jar"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[com.datomic/datomic-pro "0.9.4815.12"]]}})
