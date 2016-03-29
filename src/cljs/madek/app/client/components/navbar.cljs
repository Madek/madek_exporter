(ns madek.app.client.components.navbar
  (:require-macros [reagent.ratom :as ratom :refer [reaction]])
  (:require
    [madek.app.client.core.state :as state]
    ))

(defn navbar-connect-info []
  [:a {:href "/connect"}
   (if-let [login (and
                    (< 0 (-> @state/client-db
                             :session-expiration
                             :expiration-in-seconds))
                    (-> @state/db :connection :login))]
     [:span.text-success
      [:b login ]
      " @ "
      [:b (-> @state/db :connection :madek-url)] ]
     [:span.text-warning [:b "Please connect!" ]]
     )])

(defn show []
  (let [current-path (aget js/window "location" "pathname")]
    [:div.navbar.navbar-default
     [:div.container-fluid
      [:div.navbar-header
       [:a.navbar-brand {:class "" :href "/"} (str "Madek Exporter " madek.app/VERSION)]]
      [:ul.nav.navbar-nav.navbar-left
       [:li {:class (str (when (= "/download" current-path) " active "))}
        [:a {:href "/download"} "Export"]
        ]]
      [:form.navbar-form.navbar-right {:action "/shutdown"}
       [:button.btn.btn-danger.btn-sm {:type "submit"}
        [:i.fa.fa-power-off]
        " Shutdown " ]]
      [:ul {:class "nav navbar-nav navbar-right"}
       [:li {:class (str (when (= "/debug" current-path) " active "))}
        [:a {:href "/debug"} "Debug"]]
       [:li {:class (str (when (= "/connect" current-path) " active "))}
        (navbar-connect-info)]]
      ]]))
