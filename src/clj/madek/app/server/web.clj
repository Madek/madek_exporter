(ns madek.app.server.web
  (:require

    [madek.app.server.connection :as connection]
    [madek.app.server.export :as export]
    [madek.app.server.state :as state]
    [madek.app.server.utils :as utils]
    [madek.app.server.utils :refer [deep-merge]]

    [json-roa.client.core :as roa]

    [clojure.pprint :refer [pprint]]

    [clj-http.client :as http-client]
    [compojure.core :refer [ANY GET PATCH POST DELETE defroutes]]
    [compojure.route :refer [not-found resources]]
    [environ.core :refer [env]]
    [hiccup.core :refer [html]]
    [hiccup.page :refer [include-js include-css]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [ring.middleware.json]
    [ring.util.response :refer [response]]


    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]

    ))


(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

(def app-page
  (html
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1"}]
      ;(include-css (if (env :dev) "css/site.css" "css/site.min.css"))
      (include-css "/assets/bootstrap-3.3.6-dist/css/bootstrap.min.css" )
      (include-css "/assets/bootstrap-3.3.6-dist/css/bootstrap-theme.min.css" )
      (include-css "/assets/font-awesome-4.5.0/css/font-awesome.css" )
      (include-js "/assets/jquery-2.1.4.min.js")
      (include-js "/assets/bootstrap-3.3.6-dist/js/bootstrap.min.js")
      ]
     [:body
      mount-target
      (include-js "js/app.js")]]))


;### download entity ##########################################################

(defn get-entity-data-from-webapp [url]
  (->  url
      (http-client/get {:accept :json
                        :as :json
                        :cookies {"madek-session"
                                  {:value
                                   (-> @state/db :connection :session-token)}}})
      :body))

(defn download-entity-check [request]
  (catcher/snatch
    {:return-fn (fn [e] {:status 500 :body (thrown/stringify e)})}
    (let [url (-> request :body :entity-url)
          entity-data (get-entity-data-from-webapp url)]
      (if-not (= "Collection" (:type entity-data))
        {:status 422 :body (str "Only sets (aka. Collections) can be downloaded, "
                                "this entity es of type " (:type entity-data))}
        (do (swap! state/db
                   (fn [db dl-entity]
                     (deep-merge db
                                 {:download
                                  {:state :download-entity-checked
                                   :entity dl-entity}}))
                   (assoc
                     (select-keys entity-data [:title :uuid :type])
                     :url url))
            {:status 204})))))

(defn download-entity-delete [_]
  (swap! state/db (fn [db]
                    (merge db
                           {:download
                            {:entity nil
                             :state nil
                             }})))
  {:status 204})


;(pprint @state/db)

;##############################################################################

(defonce download-future (atom nil))

(def snatch-dl-exception-params
  {:return-fn (fn [e]
                (swap! state/db
                       (fn [db e]
                         (deep-merge db
                                     {:download
                                      {:state :failed
                                       :errors {:dowload-error (str e)}}}))
                       e))
   :throwable Throwable})

(defn start-download-future [id target-dir recursive? entry-point http-options]
  (reset! download-future
          (future
            (catcher/snatch
              {:return-fn (fn [e]
                            (swap! state/db
                                   (fn [db e]
                                     (deep-merge db
                                                 {:download
                                                  {:state :failed
                                                   :errors {:dowload-error (str e)}}}))
                                   e))
               :throwable Throwable}
              (export/download-set id target-dir recursive? entry-point http-options)
              (swap! state/db (fn [db] (assoc-in db [:download :state] :finished))))
            )))

(defn download [request]
  (if (and @download-future (not (realized? @download-future)))
    {:status 422 :body "There seems to be an ongoing download in progress!"}
    (catcher/snatch
      {:return-fn (fn [e]
                    (swap! state/db
                           (fn [db e]
                             (deep-merge db
                                         {:download
                                          {:state :failed
                                           :errors {:dowload-error (str e)}}}))
                           e))
       :throwable Throwable}
      (swap! state/db (fn [db] (assoc-in db [:download :state] :downloading)))
      (let [id (-> @state/db :download :entity :uuid)
            target-dir (-> @state/db :download-parameters :target-dir)
            recursive? (-> @state/db :download-parameters :recursive not not)
            entry-point (str (-> @state/db :connection :madek-url) "/api")
            http-options (utils/options-to-http-options
                           (-> @state/db :connection
                               (select-keys [:session-token :login :password])))]
        (start-download-future id target-dir recursive? entry-point http-options))
      {:status 202})))


(defn patch-download-item [request]
  (logging/debug 'patch-download-item {:request request})
  (let [item-id (-> request :route-params :id)
        patch-params (-> request :body)]
    (swap! state/db
           (fn [db item-id patch-params]
             (let [item-params (or (-> db :download :items (get item-id)) {})]
               (assoc-in db [:download :items item-id]
                         (deep-merge item-params
                                     patch-params))))
           item-id patch-params))
  {:status 204})

(defn delete-download [_]
  (swap! state/db (fn [db]
                    (dissoc db :download :download-entity)))
  {:status 204})

;##############################################################################

(defn patch-download-parameters [request]
  (logging/debug 'patch-download-parameters {:request request})
  (swap! state/db
         (fn [db params]
           (deep-merge db
                       {:download-parameters params}))
         (:body request))
  {:status 204})

;##############################################################################

(defn open [request]
  (logging/debug request)
  (utils/os-open (-> request :body :uri))
  {:status 201})

;##############################################################################

(defn shutdown [request]
  (future
    (Thread/sleep 3000)
    (System/exit 0))
  "Good Bye!")

;##############################################################################


(defroutes routes

  (GET "/" [] app-page)
  (GET "/about" [] app-page)
  (GET "/connect" [] app-page)
  (GET "/debug" [] app-page)
  (GET "/download" [] app-page)

  (DELETE "/download" _ #'delete-download)

  (POST "/connect" _ #'connection/connect-to-madek-server)
  (POST "/open" _ #'open)
  (PATCH "/download/items/:id" _ #'patch-download-item)

  (PATCH "/download-parameters" _ #'patch-download-parameters)


  (POST "/download" _ #'download)

  (POST "/download/entity" _ #'download-entity-check)
  (DELETE "/download/entity" _ #'download-entity-delete)

  (ANY "/shutdown" _ #'shutdown)

  (resources "/")
  (not-found "Not Found"))

(def app
  (I> wrap-handler-with-logging
      routes
      state/wrap
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      (ring.middleware.json/wrap-json-body {:keywords? true})
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params
      ring.middleware.json/wrap-json-response
      ))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
