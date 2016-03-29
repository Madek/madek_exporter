(ns madek.app.client.components.core
  (:require-macros [reagent.ratom :as ratom :refer [reaction]])
  (:require
    [madek.app.client.core.state :as state]
    [madek.app.client.alerts-local :as alerts-local]
    [madek.app.client.components.navbar :as navbar]
    [madek.app.client.components.issues :as issues]
    ))

(defn page-headers []
  [:div
   (navbar/show)
   (issues/client-issues)
   (alerts-local/alerts)])

