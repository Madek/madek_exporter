(ns madek.app.client.core.state
  (:require

    [cljs-uuid-utils.core :as uuid]
    [reagent.core :as reagent]
    [cljs.core.async :as async :refer (<! >! put! chan)]
    [taoensso.sente  :as sente :refer (cb-success?)]
    [cljsjs.moment]
    [timothypratley.patchin :as patchin]

    ))


(defonce db (reagent/atom {}))

(defonce client-db (reagent/atom {:debug false
                                  :client-id (uuid/uuid-string (uuid/make-random-uuid))}))

;### session expiration #######################################################

(defn set-session-expiation []
  (let [session-expiration-ref-time (-> @db :connection :session-expiration-ref-time)
        session-expiration-seconds (-> @db :connection :session-expiration-seconds)]
    (if-not (and session-expiration-seconds session-expiration-ref-time)
      (swap! client-db (fn [d] (dissoc d :session-expiration)))
      (let [ref-time (js/moment session-expiration-ref-time)
            expiration-time (.add ref-time session-expiration-seconds "seconds")
            fun (fn [d]
                  (assoc d :session-expiration
                         {:expiration-in-seconds (.diff expiration-time (js/moment) "seconds")
                          :expiration-in-human (.to (js/moment) expiration-time)
                          }))
            ]
        (swap! client-db fun)
      ))))

(js/setInterval set-session-expiation 1000)


;### sente ####################################################################


(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
                                  {:type :auto ; e/o #{:auto :ajax :ws}
                                   :client-id (:client-id @client-db)
                                   })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom)
  )

;### message routing ##########################################################

(defn patch-db [patch-data]
  (swap! db
         (fn [db diff]
           (patchin/patch db diff))
         (:diff patch-data)))

(defn event-msg-handler [{:as message :keys [id ?data event]}]
  ;(js/console.log (clj->js {:message message}))
  (when (= id :chsk/recv)
    (let [[event-id data] ?data]
      (when (and event-id data)
        (js/console.log (clj->js {:event-id event-id :data data}))
        (case event-id
          :madek/db (reset! db data)
          :madek/patch (patch-db data)
          )))))

(def message-router (atom nil))
(defn stop-message-router [] (when-let [stop-f @message-router] (stop-f)))
(defn start-message-router []
  (stop-message-router)
  (reset! message-router (sente/start-chsk-router! ch-chsk event-msg-handler)))

(start-message-router)

;##############################################################################


