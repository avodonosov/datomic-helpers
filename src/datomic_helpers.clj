;;;; -*- Mode: clojure; indent-tabs-mode: nil; coding: utf-8; show-trailing-whitespace: t -*-
;;;; Copyright (C) 2014 Anton Vodonosov (avodonosov@yandex.ru)
;;;; See LICENSE for details.

(ns datomic-helpers
  (:use clojure.test))

(declare tempid)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; TO-TRANSACTION

(defn- translate-value [v]
  ;; Returns a vector of two elements:
  ;; 1. The replacement for V (new :db/id value if V is a map,
  ;;    a vector with maps replaced by :db/id's if V is a vector, etc.)
  ;; 2. The sequence of maps which were replaced by their new :db/id's,
  ;;    each map already contains the :db/id.
  (letfn [(translate-values [values]
            (let [mapped (map translate-value values)]
              [(reduce conj [] (map first mapped))
               (reduce concat '() (map second mapped))]))]
    (cond (map? v) (let [id (tempid :db.part/user)
                         translated-vals (translate-values (vals v))
                         translated-map (zipmap (keys v)
                                                (first translated-vals))]
                     [id (cons (assoc translated-map :db/id id)
                               (second translated-vals))])
          (vector? v) (translate-values v)
          :else [v nil])))

(comment ;; try it
  (translate-value {:a 1 :b 2 :c {:subA 11 :subB 12}})
  (translate-value [{:a 1 :b 2}])
  )

(defn to-transaction [data-map]
  (vec (second (translate-value data-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; TO-SCHEMA-TRANSATION

(defn ext [extra-props type]
  (list 'ext type extra-props))

(defn- third [s]
  (second (rest s)))

(with-test

  (defn- strip-props [type]
    (loop [props {} inner-val type]
      (cond (vector? inner-val)
            (do (assert (= 1 (count inner-val)) (str "vector should only contain single element: " inner-val))
                (recur (assoc props :db/cardinality :db.cardinality/many)
                       (first inner-val)))
            (and (list? inner-val) (= 'ext (first inner-val)))
            (recur (merge props (third inner-val))
                   (second inner-val))
            :else
            [props inner-val])))

  (is (= [{} :db/keyword]
         (strip-props :db/keyword)))

  (is (= [{:db/cardinality :db.cardinality/many}
          :db/keyword]
         (strip-props [ :db/keyword ])))

  (is (= [{:db/index true}
          :db/keyword]
         (strip-props (ext {:db/index true} :db/keyword))))

  (is (= [{:db/index true :db/cardinality :db.cardinality/many} :db/keyword]
         (strip-props [(ext {:db/index true} :db/keyword)])))
  (is (= [{:db/index true :db/cardinality :db.cardinality/many} :db/keyword]
         (strip-props (ext {:db/index true} [ :db/keyword ])))))

(defn to-schema-transaction- [type]
  (cond (map? type) (mapcat (fn [[attr val]]
                              (if-not (symbol? val)
                                (let [[extra-props inner-val] (strip-props val)]
                                  (cons (merge {:db/ident attr
                                                :db/valueType (if (or (map? inner-val)
                                                                      (set? inner-val))
                                                                :db.type/ref
                                                                inner-val)
                                                :db/cardinality :db.cardinality/one ; just a default, may be overriden by extra-props
                                                :db.install/_attribute :db.part/db
                                                :db/id (tempid :db.part/db)}
                                               extra-props)
                                        (to-schema-transaction- inner-val)))))
                            (filter #(not= :db/ident (first %1))
                                    (seq type)))
        (set? type) (for [enum-val type]
                      {:db/ident enum-val
                       :db/id (tempid :db.part/user)})))

(defn- cleanup-duplicates [attribute-specifications]
  (let [groups (group-by :db/ident attribute-specifications)]
    (for [attr (keys groups)]
      ;; ignore :db/id as it is a different temporary
      ;; ID we generated for each (probably otherwise equal)
      ;; attribute definition
      (if (apply not= (map #(dissoc %1 :db/id)
                           (groups attr)))
        (throw (IllegalArgumentException.
                (str "Different definitions of attribute " attr " : "
                     (groups attr))))
        (first (groups attr))))))

(defn to-schema-transaction [type]
  (doall (cleanup-duplicates (to-schema-transaction- type))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Datomic API

;;; We can't directly depend on datomic peer library,
;;; because we don't want to stick to concrete
;;; implementation (-pro or -free, nor to any particular version number)
;;; and datomic does not provide datomic.api as a component
;;; we can depend upon.
;;; Therefore we use reflection to invoke datomic.Peer/tempid
;;; method.
(defn tempid [partition]
  (.invoke (.getMethod (Class/forName "datomic.Peer")
                       "tempid"
                       (into-array Class [Object]))
           nil
           (to-array [partition])))
;;;
;;; If you covnert huge amount of data and want to avoid
;;; reflection for speedup, just redefine the above
;;; function in your app, where Datomic is loaded already:
;;;
;;;   (defn datomic-helpers/tempid [partition]
;;;     (datomic.Peer/tempid partition))
