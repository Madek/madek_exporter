(ns madek.app.client.connect
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [madek.app.client.components.core :refer [page-headers]]
    [madek.app.client.core.state :as state]

    [cljs-http.client :as http]
    [cljs.core.async :refer [<!]]
    [reagent.core :as reagent]
    ))


(defonce connection (ratom/reaction (:connection @state/db)))

(defonce use-default-madek-url (reagent/atom true))

(defonce madek-url (ratom/reaction (:madek-url @connection)))

(defn madek-url-change-handler [event]
  (js/console.log "madek-url-change-handler")
  (reset! use-default-madek-url false)
  (reset! madek-url (-> event .-target .-value)))

(defn toggle-use-default-madek-url []
  (swap! use-default-madek-url (fn [state] (not state))))

(defonce token-duration (reagent/atom "6 hours"))

(defonce session-token (reagent/atom ""))

(defonce connect-enabled?
  (ratom/reaction
    (not (clojure.string/blank? @session-token))))

(defn connect [event]
  (js/console.log (str "connect!" event))

  (go (let [response (<! (http/post "/connect"
                                    {:json-params {:madek-url @madek-url
                                                   :session-token @session-token}}))]
        (prn response)
        (prn (str (select-keys response [:error-code :error-text])))

        (when (:success response)
          (swap! state/client-db (fn [db]
                                   (update-in db [:issues] dissoc :connect))))
        (when-not (:success response)
          (swap! state/client-db (fn [db resp]
                                   (assoc-in db [:issues :connect]
                                             {:title "Connect error"
                                              :type "error"
                                              :description (select-keys resp [:error-code :error-text])
                                              :dismissible false
                                              })) response ))
        )))

(defn page []
  [:div {:class "container"}
   (page-headers)

   (when (< 0 (-> @state/client-db :session-expiration :expiration-in-seconds))
     [:div.alert.alert-success
      [:span "You are connected as "
       [:b (-> @state/db :connection :login)]
       " to "
       [:b (-> @state/db :connection :madek-url)]  ". "
       "Your session expires "
       (-> @state/client-db :session-expiration :expiration-in-human) "."
       "You can now perform an " [:a {:href "/download"} "export."]
       ]])


   [:div
    [:h1 "Connect to Your Madek Instance"]
    [:div
     [:div.checkbox
      [:label
       [:input {:type "checkbox" :checked (not @use-default-madek-url)
                :on-change toggle-use-default-madek-url}]
       "Change URL"
       ]]
     [:div {:class "form-group"}
      [:label {:for "url"} "Madek URL"]
      [:input (merge {:id "url" :class "form-control" :type "url"
                      :on-change madek-url-change-handler }
                     (if @use-default-madek-url
                       {:value (:madek-url @connection)}
                       {:default-value @madek-url}))]]

     [:div.form-group
      [:label "Token will be valid for:"]
      [:select.form-control
       {:field :list
        :id :token-duration
        :value @token-duration
        :on-change #(reset! token-duration (-> % .-target .-value))
        }
       [:option "30 minutes"]
       [:option "1 hour"]
       [:option "2 hours"]
       [:option "6 hours"]
       [:option "12 hours"]
       [:option "24 hours"]
       ]]

     [:div {:class "form-group pull-right"}
      [:a.btn.btn-default
       {:href (str @madek-url "/my/session-token?duration=" @token-duration) :target "_blank"}
       "Get session-token"]]
     [:div.clearfix]

     [:div {:class "form-group"}
      [:label {:for "session-token"} "Session-token "]
      [:textarea {:id "session-token" :class "form-control" :rows "3"
                  :on-change #(reset! session-token (-> % .-target .-value))
                  :value @session-token}]]
     [:div {:class "form-group pull-right"}
      [:a.btn.btn-primary {:class (if @connect-enabled? "" "disabled")
                           :on-click connect} "Connect"]]]

    (when (:debug @state/client-db)
      [:div {:class "clearfix"}
       [:hr
        [:h2 "Debug"]
        [:div "connection:"
         [:pre (str @connection)]]
        [:div "madek-url: " @madek-url]
        ]])]])

