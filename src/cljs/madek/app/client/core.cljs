(ns madek.app.client.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require
    [madek.app]
    [madek.app.client.debug :as debug]
    [madek.app.client.download :as download]
    [madek.app.client.connect :as connect]
    [madek.app.client.components.navbar :as navbar]

    [accountant.core :as accountant]
    [reagent.core :as reagent :refer [atom]]
    [reagent.session :as session]
    [secretary.core :as secretary :include-macros true]
    ))


(defn home-page []
  [:div {:class "container"}
   (navbar/show)
   [:div {:class "text-center"}
    [:div [:h2 "The Madek Exporter"]
     [:div "Version " madek.app/VERSION]]]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/connect" []
  (session/put! :current-page #'connect/page))

(secretary/defroute "/debug" []
  (session/put! :current-page #'debug/page))

(secretary/defroute "/download" []
  (session/put! :current-page #'download/page))




;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page]
                  (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!)
  (accountant/dispatch-current!)
  (mount-root))
