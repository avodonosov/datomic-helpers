;;;; -*- Mode: clojure; indent-tabs-mode: nil; coding: utf-8; show-trailing-whitespace: t -*-
;;;;
;;;; =========================================================================================
;;;;
;;;; Demonstrates how to use DATOMIC-HELPERS to define schema and some data
;;;; for two well known Datomic sample databases:
;;;;
;;;; 1. The Seattle sample, distributed with Datomic. See original
;;;;    schema and data in
;;;;    <datomic-root>/samples/seattle/seattle-schema.edn  and seattle-data0.edn
;;;; 2. The MusicBrainz sample, see original schema at
;;;;    https://github.com/Datomic/mbrainz-sample/blob/master/schema.edn
;;;;
;;;; =========================================================================================

(ns datomic-helpers-sample
  (:require [datomic.api :as d])
  (:use clojure.test datomic-helpers))

(def db-uri "datomic:mem://dtmclj")
(d/create-database db-uri)
(def conn (d/connect db-uri))

(defn with-all [db & transactions]
  (reduce #(:db-after (d/with %1 %2))
          db
          transactions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Seattle sample

(let [db (with-all (d/db conn)
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
  (d/q '[:find ?name ?category ?neighborhood-name ?district-name
         :where [?c :community/name ?name]
                [?c :community/category ?category]
                [?c :community/neighborhood ?n]
                [?n :neighborhood/name ?neighborhood-name]
                [?n :neighborhood/district ?d]
                [?d :district/name ?district-name]
         ]
       db))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; MusicBrainz sample

(def mbrainz-schema
  {
   ;; The globally unique MusicBrainz ID for the release
   :release/gid (ext {:db/unique :db.unique/identity
                      :db/index true}
                     :db.type/uuid)

   ;; The country where the recording was released
   :release/country {:db/ident :db.type/keyword
                     ;; The name of the country
                     :country/name (ext {:db/unique :db.unique/value}
                                        :db.type/string)}

   ;; The label on which the recording was released.
   ;; An issue exists: https://github.com/Datomic/mbrainz-sample/pull/2
   :release/label {
                   ;; The globally unique MusicBrainz ID for the record label
                   :label/gid (ext {:db/unique :db.unique/identity
                                    :db/index true}
                                   :db.type/uuid)

                   ;; The name of the record label
                   :label/name (ext {:db/fulltext true
                                     :db/index true}
                                    :db.type/string)

                   ;; The name of the record label for use in alphabetical sorting
                   :label/sortName (ext {:db/index true}
                                        :db.type/string)

                   :label/type #{:label.type/distributor
                                 :label.type/holding
                                 :label.type/production
                                 :label.type/originalProduction
                                 :label.type/bootlegProduction
                                 :label.type/reissueProduction
                                 :label.type/publisher}

                   ;; The country where the record label is located
                   :label/country :db.type/ref ; country entity, see above

                   ;; The year the label started business
                   :label/startYear (ext {:db/index true}
                                         :db.type/long)

                   ;; The month the label started business
                   :label/startMonth :db.type/long

                   ;; The day the label started business
                   :label/startDay :db.type/long

                   ;; The year the label stopped business
                   :label/endYear :db.type/long

                   ;; The month the label stopped business
                   :label/endMonth :db.type/long

                   ;; The day the label stopped business
                   :label/endDay :db.type/long}

   ;; The script used in the release
   :release/script {:db/ident :db.type/keyword
                    ;; Name of written character set, e.g. Hebrew, Latin, Cyrillic
                    :script/name (ext {:db/unique :db.unique/value}
                                      :db.type/string)}

   ;; The language used in the release
   :release/language {:db/ident :db.type/keyword
                      ;; The name of the written and spoken language
                      :language/name (ext {:db/unique :db.unique/value}
                                          :db.type/string)}

   ;; The barcode on the release packaging
   :release/barcode :db.type/string

   ;; The name of the release
   :release/name (ext {:db/fulltext true
                       :db/index true}
                      :db.type/string)

   ;; The various media (CDs, vinyl records, cassette tapes, etc.) included in the release.
   :release/media (ext {:db/isComponent true}
                       [ {
                          ;; The set of tracks found on this medium
                          :medium/tracks (ext {:db/isComponent true}
                                              [ {
                                                 ;; The artists who contributed to the track
                                                 :track/artists [ {
                                                                   ;; The globally unique MusicBrainz ID for an artist
                                                                   :artist/gid (ext {:db/unique :db.unique/identity
                                                                                     :db/index true}
                                                                                    :db.type/uuid)

                                                                   ;; The artist's name
                                                                   :artist/name (ext {:db/fulltext true
                                                                                      :db/index true}
                                                                                     :db.type/string)

                                                                   ;; The artist's name for use in alphabetical sorting, e.g. Beatles, The
                                                                   :artist/sortName (ext {:db/index true}
                                                                                         :db.type/string)

                                                                   ;; The mbrainz-sample says "The artist's name for use in sorting,
                                                                   ;; e.g. Beatles, The"
                                                                   ;; (https://github.com/Datomic/mbrainz-sample/blob/master/schema.edn#L66)
                                                                   ;; but that's most likely a typo, and it must be an enum.
                                                                   ;; Issue opened: https://github.com/Datomic/mbrainz-sample/issues/3
                                                                   :artist/type #{:artist.type/person :artist.type/group :artist.type/other}

                                                                   :artist/gender #{:artist.gender/male
                                                                                    :artist.gender/female
                                                                                    :artist.gender/other}

                                                                   ;; The artist's country of origin
                                                                   :artist/country :db.type/ref ; country entity, see above

                                                                   ;; The year the artist started actively recording
                                                                   :artist/startYear (ext {:db/index true}
                                                                                          :db.type/long)

                                                                   ;; The month the artist started actively recording
                                                                   :artist/startMonth :db.type/long

                                                                   ;; The day the artist started actively recording
                                                                   :artist/startDay :db.type/long

                                                                   ;; The year the artist stopped actively recording
                                                                   :artist/endYear :db.type/long

                                                                   ;; The month the artist stopped actively recording
                                                                   :artist/endMonth :db.type/long

                                                                   ;; The day the artist stopped actively recording
                                                                   :artist/endDay :db.type/long} ]

                                                 ;; The artists who contributed to the track
                                                 :track/artistCredit (ext {:db/fulltext true}
                                                                          :db.type/string)

                                                 ;; The position of the track relative to the other tracks on the medium
                                                 :track/position :db.type/long

                                                 ;; The track name
                                                 :track/name (ext {:db/fulltext true
                                                                   :db/index true}
                                                                  :db.type/string)

                                                 ;; The duration of the track in msecs
                                                 :track/duration (ext {:db/index true}
                                                                      :db.type/long)
                                                 } ])

                          ;; The format of the medium. An enum with lots of possible values
                          :medium/format :db.type/ref

                          ;; The position of this medium in the release relative to the other media, i.e. disc 1
                          :medium/position :db.type/long

                          ;; The name of the medium itself, distinct from the name of the release
                          :medium/name (ext {:db/fulltext true}
                                            :db.type/string)
                          ;; The total number of tracks on the medium
                          :medium/trackCount :db.type/long} ] )

   ;; The type of packaging used in the release
   :release/packaging #{:release.packaging/jewelCase
                        :release.packaging/slimJewelCase
                        :release.packaging/digipak
                        :release.packaging/other
                        :release.packaging/keepCase
                        :release.packaging/none
                        :release.packaging/cardboardPaperSleeve}

   ;; The year of the release
   :release/year (ext {:db/index true}
                      :db.type/long)

   ;; The month of the release
   :release/month :db.type/long

   ;; The day of the release
   :release/day :db.type/long

   ;; The string represenation of the artist(s) to be credited on the release
   :release/artistCredit (ext {:db/fulltext true}
                              :db.type/string)

   ;; The set of artists contributing to the release
   :release/artists [ :db.type/ref ; artist entity, see above
                     ]

   ;; This release is the physical manifestation of the
   ;; associated abstract release, e.g. the 1984 US vinyl release of
   ;; "The Wall" by Columbia, as opposed to the 2000 US CD release of
   ;; "The Wall" by Capitol Records.
   :release/abstractRelease  {
                              ;; The globally unique MusicBrainz ID for the abstract release
                              :abstractRelease/gid (ext {:db/unique :db.unique/identity
                                                         :db/index true}
                                                        :db.type/uuid)

                              ;; The name of the abstract release
                              :abstractRelease/name (ext {:db/index true}
                                                         :db.type/string)

                              :abstractRelease/type #{:release.type/album
                                                      :release.type/single
                                                      :release.type/ep
                                                      :release.type/audiobook
                                                      :release.type/other}

                              ;; The set of artists contributing to the abstract release
                              :abstractRelease/artists [ :db.type/ref ]

                              ;; The string represenation of the artist(s) to be credited on the abstract release
                              :abstractRelease/artistCredit (ext {:db/fulltext true}
                                                                 :db.type/string) }

   ;; The status of the release
   :release/status (ext {:db/index true}
                        :db.type/string)
   })

(def mbrainz-enums
  [{:db/ident :language/eng, :language/name "English"}
   {:db/ident :language/fra, :language/name "French"}
   {:db/ident :script/Latn, :script/name "Latin"}
   {:db/ident :country/GB, :country/name "Great Britan"}
   {:db/ident :country/FR, :country/name "France"}
   {:db/ident :country/MC, :country/name "Monaco"}
   {:db/ident :medium.format/cd}])

(def mbrainz-data
  [{:release/name "On n'est pas sérieux quand on a dix-sept ans",
    :release/artistCredit "Léo Ferré",
    :release/language :language/fra,
    :release/status "Official",
    :release/year 2000,
    :release/gid #uuid "d189a947-c7cd-4005-8510-455e00d156d6",
    :release/country :country/FR,
    :release/script :script/Latn,
    :release/barcode "794881501021",
    :release/label {:label/gid #uuid "49561e52-9e40-4eb1-961b-9fde188c8b34",
                    :label/name "La Mémoire et la Mer",
                    :label/sortName "La Mémoire et la Mer",
                    :label/type :label.type/reissueProduction,
                    :label/country :country/FR},
    :release/artists [{:artist/name "Léo Ferré",
                       :artist/startDay 24,
                       :artist/startYear 1916,
                       :artist/gender :artist.gender/male,
                       :artist/startMonth 8,
                       :artist/endDay 14,
                       :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                       :artist/country :country/MC,
                       :artist/type :artist.type/person,
                       :artist/endYear 1993,
                       :artist/sortName "Ferré, Léo",
                       :artist/endMonth 7}],
    :release/media [{:medium/format :medium.format/cd,
                     :medium/position 1,
                     :medium/trackCount 16,
                     :medium/tracks
                     [{:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 10,
                       :track/name "Le Manque",
                       :track/duration 445800}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 9,
                       :track/name "Je te donne ces vers",
                       :track/duration 78133}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 12,
                       :track/name "Si tu ne mourus pas",
                       :track/duration 215226}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 11,
                       :track/name "Visa pour l'Amérique",
                       :track/duration 318306}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 14,
                       :track/name "L'Examen de minuit (et) Dorothée",
                       :track/duration 324266}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 13,
                       :track/name "Personne",
                       :track/duration 389400}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 16,
                       :track/name "Lorsque tu me liras",
                       :track/duration 144626}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 15,
                       :track/name "Le Faux Poète",
                       :track/duration 183373}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 2,
                       :track/name "Colloque sentimental",
                       :track/duration 215266}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 1,
                       :track/name "On n'est pas sérieux quand on a dix-sept ans",
                       :track/duration 262266}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}
                        ],
                       :track/artistCredit "Léo Ferré",
                       :track/position 4,
                       :track/name "Les Morts qui vivent",
                       :track/duration 353760}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 3,
                       :track/name "Les Cloches (et) la Tzigane",
                       :track/duration 259800}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 6,
                       :track/name "Gaby",
                       :track/duration 291373}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 5,
                       :track/name "Tout ce que tu veux",
                       :track/duration 351666}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 8,
                       :track/name "Le Sommeil du juste",
                       :track/duration 106800}
                      {:track/artists
                       [{:artist/name "Léo Ferré",
                         :artist/startDay 24,
                         :artist/startYear 1916,
                         :artist/gender :artist.gender/male,
                         :artist/startMonth 8,
                         :artist/endDay 14,
                         :artist/gid #uuid "15b1cbac-060a-4136-9e07-4622c52c0f60",
                         :artist/country :country/MC,
                         :artist/type :artist.type/person,
                         :artist/endYear 1993,
                         :artist/sortName "Ferré, Léo",
                         :artist/endMonth 7}],
                       :track/artistCredit "Léo Ferré",
                       :track/position 7,
                       :track/name "Marie",
                       :track/duration 359226}]}]}

   {:release/name "Snow on Snow",
    :release/artistCredit "Stars Of Aviation",
    :release/status "Official",
    :release/gid #uuid "123a923b-f710-4057-9f40-07157c6e3a11",
    :release/country :country/GB,
    :release/language :language/eng,
    :release/script :script/Latn,
    :release/year 2003,
    :release/month 5
    :release/day 1,
    :release/label {:label/gid #uuid "b9a3521b-469f-4b19-8915-561738b86330",
                    :label/name "Kitchen Records",
                    :label/sortName "Kitchen Records",
                    :label/country :country/GB},
    :release/artists [{:artist/gid #uuid "0530acce-9a4a-4b6b-8b49-526e74584826",
                       :artist/name "Stars Of Aviation",
                       :artist/sortName "Stars Of Aviation"}],
    :release/media [{:medium/tracks
                     [{:track/artists
                       [{:artist/gid #uuid "0530acce-9a4a-4b6b-8b49-526e74584826",
                         :artist/name "Stars Of Aviation",
                         :artist/sortName "Stars Of Aviation"}],
                       :track/artistCredit "Stars Of Aviation",
                       :track/position 1,
                       :track/name "Snow on Snow",
                       :track/duration 293000}
                      {:track/artists
                       [{:artist/gid #uuid "0530acce-9a4a-4b6b-8b49-526e74584826"
                         ,
                         :artist/name "Stars Of Aviation",
                         :artist/sortName "Stars Of Aviation"}],
                       :track/artistCredit "Stars Of Aviation",
                       :track/position 3,
                       :track/name
                       "Stars of Aviation are singing about the summer, but is it going to be sunny, Carol?",
                       :track/duration 207000}
                      {:track/artists
                       [{:artist/gid #uuid "0530acce-9a4a-4b6b-8b49-526e74584826",
                         :artist/name "Stars Of Aviation",
                         :artist/sortName "Stars Of Aviation"}],
                       :track/artistCredit "Stars Of Aviation",
                       :track/position 2,
                       :track/name "Illumined",
                       :track/duration 149000}
                      {:track/artists
                       [{:artist/gid #uuid "0530acce-9a4a-4b6b-8b49-526e74584826",
                         :artist/name "Stars Of Aviation",
                         :artist/sortName "Stars Of Aviation"}],
                       :track/artistCredit "Stars Of Aviation",
                       :track/position 4,
                       :track/name "Love Is Only in Your Mind",
                       :track/duration 135000}],
                     :medium/format :medium.format/cd,
                     :medium/position 1,
                     :medium/trackCount 4}]}])

(let [db (with-all (d/db conn)
           (to-schema-transaction mbrainz-schema)
           (mapcat to-transaction
                   mbrainz-enums)
           (mapcat to-transaction
                   mbrainz-data))]
  (d/q '[:find ?name ?track-position ?track-name
         :where [?r :release/name ?name]
                [?r :release/media ?m]
                [?m :medium/tracks ?t]
                [?t :track/name ?track-name]
                [?t :track/position ?track-position]]
       db))
