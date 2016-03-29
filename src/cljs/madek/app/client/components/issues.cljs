(ns madek.app.client.components.issues
  (:require-macros [reagent.ratom :as ratom :refer [reaction]])
  (:require
    [madek.app.client.core.state :as state]

    [cljs.pprint :refer [pprint]]
    ))

(defn issue-class [issue]
  (case (:type issue)
    "error" "alert-danger"
    ""))


(defn dismiss-issue [id]
  (pprint id)
  (swap! state/client-db
         (fn [db id]
           (dissoc db :issues id))
         id ))

(defn client-issues []
  [:div.client-issues
   (for [[id issue] (:issues @state/client-db)]
     (let [dismissible (:dismissible issue)]
       [:div.alert {:class
                    (clojure.string/join
                      " " [(issue-class issue)
                           (when dismissible
                             "alert-dismissible")])}
        (when dismissible
          [:button.button.close
           {:on-click #(dismiss-issue id)} "×"])
        [:h3 (str (:title issue))]
        [:pre (str (:description issue))]]
       ))])
