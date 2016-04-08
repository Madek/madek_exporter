(ns madek.app.client.download
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [madek.app.client.core.state :as state]
    [madek.app.client.components.core :refer [page-headers]]

    [cljs.pprint :refer [pprint]]
    [cljs-http.client :as http]
    [cljs.core.async :refer [<!]]
    [reagent.core :as reagent]
    ))

;(enable-console-print!)

(defonce entity-url (reagent/atom ""))

(defonce madek-url (ratom/reaction (-> @state/db :connection :madek-url)))

(defonce download (ratom/reaction (:download @state/db)))

(defonce download-parameters (ratom/reaction (:download-parameters @state/db)))

(def download-items (ratom/reaction (->> @state/db
                                        :download
                                        :items
                                        (map (fn [[k v]] (assoc v :item-id k)))
                                        (sort-by #(:download_started-at %))
                                        )))

(defn eval-response [response issue-key issue-title]
  (prn response)
  (prn (str (select-keys response [:error-code :error-text])))
  (when (:success response)
    (swap! state/client-db
           (fn [db]
             (update-in db [:issues] dissoc issue-key))))
  (when-not (:success response)
    (swap! state/client-db
           (fn [db resp]
             (assoc-in db [:issues issue-key]
                       {:title issue-title
                        :type "error"
                        :description (select-keys resp [:error-code :error-text :body])
                        :dismissible true
                        })) response )))


(defn start-download [_]
  (go (let [response (<! (http/post
                           "/download"
                           {:json-params {}}))]
        (eval-response response :start-download
                       "Start Export Error"))))


(defn http-request [req-params]
  (pprint req-params)
  (go (let [response (<! (http/request req-params))]
        (eval-response response
                       :request-error
                       "Request Error"))))


;##############################################################################

(defn item-url [item-id item-type]
  (when-let [part (case item-type
                    "Collection" "/sets/"
                    "MediaEntry" "/entries/"
                    nil)]
    (str @madek-url part item-id)))

(defn render-raw-data [id item]
  [:div.row.data
   [:div.col-sm-1
    [:button.btn.btn-sm
     {:on-click #(http-request
                   {:method :patch
                    :url (str "/download/items/" id)
                    :json-params {:ui-expand-data (not (:ui-expand-data item))}})}
     (if (:ui-expand-data item)
       [:i.fa.fa-caret-down]
       [:i.fa.fa-caret-right])
     ]]
   [:div.col-sm-11
    (if-not (:ui-expand-data item)
      [:pre "Raw data …" ]
      [:pre (with-out-str (pprint item))
       ])]])

(defn render-sub-items [id item-type item]
  [:div
   [:ul.list-group
    (when-let [url (item-url id item-type)]
      [:li.list-group-item
       [:a {:href url} [:i.fa.fa-globe] " " url]])
    (when-let [path (:path item)]
      [:li.list-group-item
       [:a {:href "#"
            :on-click #(http-request
                         {:method :post
                          :url (str "/open")
                          :json-params {:uri path}})}
        [:i.fa.fa-file] " " path]])
    (for [[s t] (-> item :links)]
      [:li.list-group-item
       [:i.fa.fa-link] " " s])]])

(defn render-item [item]
  (let [id (:item-id item)
        item-type (:type item)]
    [:div.well {:id id}
     [:h4
      [:button.btn.btn-sm
       {:on-click #(http-request
                     {:method :patch
                      :url (str "/download/items/" id)
                      :json-params {:ui-expand (not (:ui-expand item))}})}
       (if (:ui-expand item)
         [:i.fa.fa-caret-down]
         [:i.fa.fa-caret-right])]
      [:span " " (:type item) " "
       (if-let [title (:title item)]
         (str "\"" title "\"")
         id)]]
     [:div.progress
      (let [pclass
            (case (:state item)
              "passed" "progress-bar-success"
              "failed" "progress-bar-danger"
              "progress-bar-warning progress-bar-striped active")]
        [:div.progress-bar {:class pclass :style {:width "100%"}}])]

     (when (:ui-expand item)
       [:div
        (render-sub-items id item-type item)
        (render-raw-data id item)]
       )]))

;### download errors ##########################################################

(defn render-download-errors []
  (for [[_ error] (-> @state/db :download :errors)]
    [:div.alert.alert-danger
     [:pre error]]))

;### download #################################################################

(defn render-download-items []
  (doall (->> @download-items
              (map render-item))))

(defn render-download []
  [:div.download
   [:h2
    (case (:state @download)
      :finished [:span.text-success "Exported"]
      :downloading "Exporting"
      :failed [:span.text-danger "Export Error"])]
   [:div.row {:style {:margin-bottom "20px"}}
    [:div.col-md-12
     [:section.form.text-center
      (case (:state @download)
        :finished [:button.btn.btn-success
                   {:on-click #(http-request
                                 {:method :delete :url "/download" })}
                   " Dismiss "
                   [:i.fa.fa-remove]]
        :failed [:button.btn.btn-danger
                 {:on-click #(http-request
                               {:method :delete :url "/download" })}
                 " Dismiss "
                 [:i.fa.fa-remove]]
        "")
      ]]]
   [:div.download-errors
    (render-download-errors)]
   [:div.download-items
    [:div (render-download-items)]
    ]])



;### state ####################################################################

(defn download-entity-check [_]
  (go (let [response (<! (http/post
                           "/download/entity"
                           {:json-params {:entity-url @entity-url }}))]
        (eval-response response :download-entity-check
                       "Pre Download Check Error"))))

(defn render-pre-check-form []
  [:section.pre-download-check
   [:h2 "Pre Export Check"]
   [:section.form
    [:div.form-group
     [:label {:for "entity-url"} "URL of the set to be exported:"]
     [:input.form-control {:type "url"
                           :value @entity-url
                           :on-change #(reset! entity-url (-> % .-target .-value))}]]
    [:div.clearfix
     [:button.btn.btn-primary.pull-right
      {:on-click download-entity-check}
      "Perform pre export check"]]]])


;### download form ############################################################

(defonce download-entity (ratom/reaction (-> @download :entity)))

(defn download-entity-name []
  [:a {:href (:url @download-entity)}
   (:type @download-entity)
   " \"" [:em (:title @download-entity)] "\" "])

(defn toggle-recursive-download-parameter [_]
  (http-request
    {:method :patch
     :url "/download-parameters"
     :json-params {:recursive (-> @download-parameters :recursive not)}})
  )

(defn render-download-form []
  [:section.start-download
   [:h2 "Export " (download-entity-name)]
   [:div.form-group
    [:label {:for "uuid"} "UUID"]
    [:input.form-control {:type "text"
                          :readonly :readonly
                          :disabled true
                          :value (:uuid @download-entity)}]]
   (when (= "Collection" (:type @download-entity))
     (let [recursive?  (-> @download-parameters :recursive not not)]
       [:div.checkbox
        [:label
         [:input#recursive
          {:type "checkbox"
           :checked recursive?
           :on-click toggle-recursive-download-parameter
           }]
         "Recursive"]
        (when recursive?
          [:div.alert.alert-info
           "Heads up: Every unique MediaEntry will be downloaded only once.
           Additional appearances of an already downloaded MediaEntry will
           be \"symlinked\". The same holds true for Sets. If a Set is
           a descendant of itself it will be downloaded only once and
           ever additional appearance will be \"symlinked\" to the location of
           the first download."])]))

   [:div.form-group
    [:label {:for "target-dir"} "Target Directory"]
    [:input.form-control {:type "text"
                          :readonly :readonly
                          :disabled true
                          :value (-> @download-parameters
                                     :target-dir str)}]]

   [:div.clearfix
    [:button.btn.btn-warning.pull-left
     {:on-click #(http-request
                   {:method :delete :url "/download/entity" })}
     [:i.fa.fa-arrow-circle-left " "
      "Back to pre download check"]]
    [:button.btn.btn-primary.pull-right
     {:on-click #(http/request
                   {:method :post
                    :url "/download"
                    :json-params {} })}
     [:i.fa.fa-download]
     [:span " Export "]
     [:i.fa.fa-arrow-circle-right]]]])

;### page #####################################################################

(defn page []

  [:div {:class "container"}

   (page-headers)

   (case (-> @state/db :download :state)
     :download-entity-checked (render-download-form)
     :downloading (render-download)
     :failed (render-download)
     :finished (render-download)
     (render-pre-check-form)
     )

   ;[:div.pre-check (pre-check-component)]
   ;[:div.download-entity (download-entity-component)]
   ;[:div.download-entity (download-component)]

   (when (:debug @state/client-db)
     [:hr
      [:h2 "Debug"]
      [:div "download-entity"
       [:pre (with-out-str (pprint @download-entity))]]
      [:div "download-parameters"
       [:pre (with-out-str (pprint @download-parameters))]]
      [:div "download"
       [:pre (with-out-str (pprint @download))]]])
   ])
