;;;; -*- Mode: clojure; indent-tabs-mode: nil; coding: utf-8; show-trailing-whitespace: t -*-
;;;; Copyright (C) 2014 Anton Vodonosov (avodonosov@yandex.ru)
;;;; See LICENSE for details.

(ns datomic-helpers
  (:use clojure.test))

(declare tempid)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; TO-TRANSACTION

;;; Support for more convenient DB population.
;;; Instead of referencing entities by temp IDs manually,
;;; we allow natural Clojure nested data structures.
;;;
;;; The TO-TRANSACTION function processes each map,
;;; asigns it a :db/id attribute.
;;;
;;; If the map key refers to another map, the reference
;;; is replaced by :db/id of the child map processed recursively.
;;;
;;; If the map key refers to a vector, the vector is processed in
;;; similar faction - all its map elements are replaced by :db/ids
;;; assigned to them in recursive processing.
;;;
;;; All other values (numbers, strings, dates, etc) are left as is.
;;;
;;; In result, we translate a nested Clojure data structure
;;; into a sequence of Datomic transaction maps,
;;; which populate database with a set of inter-linked entities.

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

;;; We represent schema of Datomic enities by Cloujure maps,
;;; and have a little utility which generates Datomic attribute
;;; specification for every map key.
;;;
;;; The schema notation is meant to be intuitively understandable,
;;; because it resembles actual shape of entities as we see them
;;; in Clojure via Datomic Entity API.
;;;
;;; Here are the precise rules:
;;;
;;; Map keys are attribute idents, the key values are attribute types.
;;;
;;; The type specification may be either:
;;; - Normal datomic types: :db.type/string, :db.type/float, etc.
;;; - Clojure map means an entity. It translates to :db.type/ref type,
;;;   and the map is processed recursively to define all its attributes too.
;;;
;;;   If your specify that your entity has :db/ident attribute, 
;;;   no attribute definition is generated for it
;;;   (because Datomic already has definition for it).
;;;   Thus :db/ident in your entities just serves human readers
;;;   of your schema.
;;;
;;; - Vector means the attibute will have :db.cardinality/many;
;;;   The attirubte type is specified by the nested vector element
;;;   (thus only single element vectors make sense)
;;; - An expression (EXT <extra properties> <typespec>) may be
;;;   used to annotate attribute type with additional schema properties.
;;;   For example: :event/category (ext {:db/index true} :db.type/keyword)
;;; - If several entities share attribute with the same name,
;;;   you may either repeat the attribute type definition,
;;;   or just use any symbol in place of attribute type,
;;;   in this case the attribute will be ignored.
;;;   For example: :schedule/timing-type 'see-above
;;;
;;; If the same entity type is referenced from several places,
;;; you may either repeat the type definition,
;;; or just use :db.type/ref in the second place.
;;;
;;; If same attribute was repeated with different definitions,
;;; an exception is thrown.


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
