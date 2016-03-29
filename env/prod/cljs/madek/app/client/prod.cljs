(ns madek.app.client.prod
  (:require [madek.app.client.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
