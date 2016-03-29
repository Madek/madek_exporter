(ns madek.app.client.alerts-local
  (:require-macros [reagent.ratom :as ratom :refer [reaction]])
  (:require
    [madek.app.client.core.state :as state]
    )
  )

(def connect-link
  [:span
   "Please (re-)"
   [:a {:href "/connect"} "connect."]])


(def alerts-atom
  (ratom/reaction
    (merge {}
           (when-let [exp-in-secs (-> @state/client-db
                                      :session-expiration
                                      :expiration-in-seconds)]
             (cond
               (> 0 exp-in-secs)
               {:expiration {:class "alert-danger"
                             :title "Session-Expired"
                             :text (str "The session expired "
                                        (-> @state/client-db
                                            :session-expiration
                                            :expiration-in-human)
                                        ".") }}
               (> 3600 exp-in-secs)
               {:expiration {:class "alert-warning"
                             :title "Session-Expiration"
                             :text (str "The session will expire "
                                        (-> @state/client-db
                                            :session-expiration
                                            :expiration-in-human)
                                        ".") }}

               :else {}
             )))))

(defn alerts []
  [:div.client-alerts
   (for [[_ alert] @alerts-atom]
     [:div.alert {:class (:class alert)}
      [:h3 (:title alert)]
      [:p (:text alert)]])])
