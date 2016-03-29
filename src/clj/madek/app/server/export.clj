(ns madek.app.server.export
  (:require
    [madek.app.server.state :as state]
    [madek.app.server.export.meta-data :as meta-data :refer [get-metadata write-meta-data]]
    [madek.app.server.export.files :as files :refer [download-media-files]]
    [madek.app.server.utils :refer [deep-merge]]


    [json-roa.client.core :as roa]
    [clj-time.core :as time]

    [cheshire.core :as cheshire]
    [clojure.java.io :as io]


    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]
    [logbug.debug :as debug :refer [identity-with-logging I> I>>]]
    )

    (:import
    [java.io File])
  )


;### title and prefix #########################################################

(defn get-title [media-resource]
  (-> media-resource
      (roa/relation :meta-data)
      (roa/get {:meta_keys (cheshire/generate-string ["madek_core:title"])})
      roa/coll-seq
      first
      (roa/get {})
      roa/data
      :value))

(defn useableFileName [s]
  (.replaceAll s "[^a-zA-Z0-9 ]" ""))

(defn path-prefix [media-resource]
  (let [prefix
        (if-let [title (get-title media-resource)]
          (str (useableFileName title) "_")
          "")]
    (str prefix (-> media-resource roa/data :id))))


;### DL Media-Entry ###########################################################

(defn set-item-to-finished [id]
  (swap! state/db
         (fn [db id]
           (deep-merge db
                       {:download
                        {:items
                         {id
                          {:state "passed"
                           :download_finished-at (str (time/now))}}}}))
         id))

(defn download-media-entry [dir-path media-entry-rel]
  (catcher/with-logging {}
    (let [media-entry (roa/get media-entry-rel {})
          id (-> media-entry roa/data :id)
          entry-prefix-path (path-prefix media-entry)
          entry-dir-path (str dir-path File/separator entry-prefix-path)
          meta-data (get-metadata media-entry)]
      (swap! state/db (fn [db uuid media-entry]
                        (assoc-in db [:download :items (str id)] media-entry))
             id (assoc (roa/data media-entry)
                       :state "downloading"
                       :errors {}
                       :type "MediaEntry"
                       :path entry-dir-path
                       :download_started-at (str (time/now))))
      (io/make-parents entry-dir-path)
      (write-meta-data entry-dir-path meta-data id)
      (download-media-files entry-dir-path media-entry)
      (set-item-to-finished id))))


;### check credentials ########################################################

(defn check-credentials [api-entry-point api-http-opts]
  (let [response (-> (roa/get-root api-entry-point :default-conn-opts api-http-opts)
                     (roa/relation :auth-info)
                     (roa/get {}))]
    (logging/debug (-> response roa/data))))


;### DL Set ###################################################################

(defn download-set [id dl-path api-entry-point api-http-opts]
  (swap! state/db (fn [db id] (deep-merge db {:download {:items {id {}}}})) id)
  (catcher/with-logging {}
    (let [me-get-opts (merge {:collection_id id}
                             (if (or (:basic-auth api-http-opts)
                                     (-> api-http-opts :cookies (get "madek-session")))
                               {:me_get_full_size "true"}
                               {:public_get_full_size "true"}))
          collection (-> (roa/get-root api-entry-point
                                       :default-conn-opts api-http-opts)
                         (roa/relation :collection )
                         (roa/get {:id id}))
          path-prefix (path-prefix collection)
          target-dir-path (str dl-path File/separator path-prefix)
          meta-data (get-metadata collection)]
      (swap! state/db (fn [db uuid collection]
                        (assoc-in db [:download :items (str id)] collection))
             id (assoc (roa/data collection)
                       :state "downloading"
                       :errors {}
                       :type "Collection"
                       :path target-dir-path
                       :download_started-at (str (time/now))))
      (io/make-parents target-dir-path)
      (write-meta-data target-dir-path meta-data id)
      (doseq [me (I> identity-with-logging
                     (roa/get-root api-entry-point
                                   :default-conn-opts api-http-opts)
                     (roa/relation :media-entries)
                     (roa/get me-get-opts)
                     roa/coll-seq)]
        (download-media-entry target-dir-path me))
      (set-item-to-finished id))))
