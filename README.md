When populating a Datomic database I found it tedious
to manually deal with temp IDs to refer entities
to each other. It is difficult to both write and read.

I created a simple function `TO-TRANSACTION` which accepts
natural Clojure data structure (nested maps, vectors)
and generates a Datomic transaction to populate DB with
these interlinked entities - temp IDs are assigned automatically,
and references to nested entities (maps) are replaced by their temp IDs.

Also, when working with DB schema, it was inconvenient
to work with plain long list of attributes. I quickly loose
track of how entities are interlinked, difficult to see how I can
improve the schema, difficult to consult it when I write queries.

So `TO-SCHEMA-TRANSACTION` function helps to generate a schema-defining
transaction from a template, which resembles real shape of data
how we see it through the Entity API.

Example how we can define schema for the well known
Seattle sample (distributed with Datomic in
the _&lt;datomic-root&gt;/samples/seattle/seattle-schema.edn_ and _seattle-data0.edn_):

```clojure

(to-schema-transaction
  {:community/name (ext {:db/fulltext true}
                        :db.type/string)
   :community/url :db.type/string
   :community/neighborhood {:neighborhood/name :db.type/string
                            :neighborhood/district {:district/name :db.type/string
                                                    :district/region #{:region/n
                                                                       :region/ne
                                                                       :region/e
                                                                       :region/se
                                                                       :region/s
                                                                       :region/sw
                                                                       :region/w
                                                                       :region/nw}}}
   :community/category [ (ext {:db/fulltext true}
                              :db.type/string) ]

   :community/orgtype #{:community.orgtype/community
                        :community.orgtype/commercial
                        :community.orgtype/nonprofit
                        :community.orgtype/personal}

   :community/type [ #{:community.type/email-list
                       :community.type/twitter
                       :community.type/facebook-page
                       :community.type/blog
                       :community.type/website
                       :community.type/wiki
                       :community.type/myspace
                       :community.type/ning} ] })
```
and some data for it:
```clojure
(mapcat to-transaction
        [{:community/name "15th Ave Community",
          :community/category ["15th avenue residents"]
          :community/orgtype :community.orgtype/community
          :community/type :community.type/email-list
          :community/url "http://groups.yahoo.com/group/15thAve_Community/"
          :community/neighborhood {:neighborhood/name "Capitol Hill",
                                   :neighborhood/district {:district/region :region/e
                                                           :district/name "East"}}}

         {:community/category ["neighborhood association"]
          :community/orgtype :community.orgtype/community
          :community/type :community.type/email-list
          :community/name "Admiral Neighborhood Association"
          :community/url "http://groups.yahoo.com/group/AdmiralNeighborhood/"
          :community/neighborhood {:neighborhood/name "Admiral (West Seattle)"
                                   :neighborhood/district {:district/region :region/sw
                                                           :district/name "Southwest"}}}

         {:community/category ["members of the Alki Community Council and residents of the Alki Beach neighborhood"]
          :community/orgtype :community.orgtype/community
          :community/type :community.type/email-list
          :community/name "Alki News"
          :community/url "http://groups.yahoo.com/group/alkibeachcommunity/"
          :community/neighborhood {:neighborhood/name "Alki"
                                   :neighborhood/district {:district/name "Southwest"}}}

         {:community/category ["news" "council meetings"]
          :community/orgtype :community.orgtype/community
          :community/type :community.type/blog
          :community/name "Alki News/Alki Community Council"
          :community/url "http://alkinews.wordpress.com/"
          :community/neighborhood {:neighborhood/name "Alki"}}
         {:district/name "Southwest"}

         {:community/category ["community council"]
          :community/orgtype :community.orgtype/community
          :community/type :community.type/website
          :community/name "All About Belltown"
          :community/url "http://www.belltown.org/"
          :community/neighborhood {:neighborhood/name "Belltown"
                                   :neighborhood/district {:district/region :region/w
                                                           :district/name "Downtown"}}}]))]   
```
The complete _[datomic_helpers_sample.clj](datomic_helpers_sample.clj)_
script demonstrates running the above Seattle sample,
and also schema and data for another Datomic sample - [MusicBrainz] (https://github.com/Datomic/mbrainz-sample).

The notation is meant to be intuitively understandable,
and here are the precise rules:

`(to-schema-transaction type)`
----------------------------

We represent schema of Datomic entities by Clojure maps.
Map keys are attribute idents, the key values are attribute types.

The type specification may be either:
- Normal Datomic types: `:db.type/string`, `:db.type/float`, etc.
- Clojure map - means an entity. It translates to `:db.type/ref` type,
  and the map is processed recursively to define all its attributes too.

  If you specify that your entity has `:db/ident` attribute,
  no attribute definition is generated for it
  (because Datomic already has definition for `:db/ident`).
  Thus `:db/ident` in entity types just serves human readers
  of your schema.

- Vector means the attibute will have `:db.cardinality/many`.
  The attirubte type is specified by the nested vector element
  (thus only single element vectors make sense).
  For example, an attribute stroing multiple strings:
  
  ```clojure
     :community/category [ :db.type/string ]
  ```
- Set means an enum. The attribute is given type `:db.type/ref`,
  and every element of the set is used as `:db/ident` for a
  new, separate entity.
- An expression `(ext <extra properties> <typespec>)` may be
  used to annotate attribute type with additional schema properties.
  For example:

  ```clojure
      :community/category [ (ext {:db/fulltext true}
                                 :db.type/string) ]
  ```
- If several entities share attribute with the same name,
  you may either repeat the attribute type,
  or just use any symbol in place of the attribute type,
  in which case the attribute appearence will be ignored:
  ```clojure
     :some/repeated-attribute 'defined-above
  ```

- If the same entity type is referenced from several places,
  you may either repeat the entity type definition,
  or just use `:db.type/ref` in the second appearance.

If the repeated attribute definitions are different,
an exception is thrown.

`(to-transaction data-map)`
-------------------------

Extends `data-map` with `:db/id` attribute.

If a `data-map` key refers to another map, the reference
value is replaced by `:db/id` of the child map processed recursively.

If a key refers to a vector, the vector is processed in
similar fashion - all its map elements are replaced by `:db/id`'s
assigned to them in recursive processing.

All other values (numbers, strings, dates, etc) are left as is.

This processing turns every map encountered into a valid
Datomic transaction map.

Returns a sequence of all those transaction maps, which
may be passed to `datomic.api/transact` to populate
database with the required set of inter-linked entities
with attributes.

----

I think the notation may be improved, but the current
form is enough for me, and helps me significantly.

# Install

Leiningen
```clojure
[datomic-helpers "1.0.0"]
```

Gradle
```
compile "datomic-helpers:datomic-helpers:1.0.0"
```

Maven
```
<dependency>
  <groupId>datomic-helpers</groupId>
  <artifactId>datomic-helpers</artifactId>
  <version>1.0.0</version>
</dependency>
```
