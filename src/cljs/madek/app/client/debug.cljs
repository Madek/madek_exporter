(ns madek.app.client.debug
  (:require
    [madek.app.client.alerts-local :as alerts-local]
    [madek.app.client.core.state :as state]
    [madek.app.client.components.navbar :as navbar]
    [madek.app.client.components.issues :as issues]

    [reagent.session :as session]
    [cljs.pprint :refer [pprint]]
    [cljsjs.moment]
    ))

(defn toggle-debug-value []
  (swap! state/client-db (fn [client-db]
                           (assoc client-db :debug (not (:debug client-db))))))

(defn page []
  [:div {:class "container"}
   (navbar/show)
   (issues/client-issues)
   (alerts-local/alerts)

   [:form
    [:div.checkbox
     [:label
      [:input {:type "checkbox"
               :checked (:debug @state/client-db)
               :on-change toggle-debug-value}]
      "Show debug info"
      ]]]

   (when (:debug @state/client-db)
     [:div [:h1 "Debug"]
      [:div
       [:h2 "Alerts-Local"]
       [:pre (with-out-str (pprint @alerts-local/alerts-atom))]]
      [:div
       [:h2 "Reagent Session State"]
       [:pre (aget js/window "location" "pathname")]
       [:pre (with-out-str (pprint @session/state))]]

      [:div
       [:h2 "Client-DB"]
       [:pre (with-out-str (pprint @state/client-db))]]
      [:div
       [:h2 "App-DB"]
       [:pre (with-out-str (pprint @state/db))]]])])
