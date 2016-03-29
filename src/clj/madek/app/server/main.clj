(ns madek.app.server.main
  (:gen-class)
  (:require
    [madek.app.server.server :as server]
    [madek.app.server.state :as state]
    [madek.app.server.utils :as utils :refer [exit]]


    [environ.core :refer [env]]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as string]
    [clojure.tools.cli :refer [parse-opts]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    ))

(def cli-options
  [
   ["-u" "--madek-url  URL" "Madek URL"
    :default (or (env :madek-url) "http://staging-v3-pdata.madek.zhdk.ch")]
   ["-d" "--download-dir DIRECTORY" "Download directory"
    :default (string/join java.io.File/separator
                          [(System/getProperty "user.home")
                           "Downloads" "Madek"])]
   ["-p" "--port PORT" "Port number"
    :default 3131
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 1024 % 0x10000) "Must be a number between 1024 and 65536"]]
   ["-i" "--interface INTERFACE" "Interface bind address"
    :default "localhost"]

   ["-h" "--help"]])

(defn usage [options-summary & more]
  (->> ["Madek APP"
        ""
        "Options:"
        options-summary
        ""
        ""
        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (string/join \newline)))

(defn -main [& args]
  (logbug.thrown/reset-ns-filter-regex #".*madek.*")
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [_] (System/exit -1))}
    (let [{:keys [options arguments errors summary]}
          (parse-opts args cli-options)]
      (cond
        (:help options) (exit 0 (usage summary {:options options})))
      (println (usage summary {:options options}))
      (let [{target-dir :download-dir madke-url :madek-url} options]
        (state/initialize
          {:download-parameters
           {:target-dir target-dir }
           :connection
           {:madek-url madke-url}}))
      (let[{port :port host :interface} options]
        (server/initialize
          {:port port
           :host host}))
      )))


;(-> (Desktop/getDesktop)(.browse (URI. "file:///Users/Thomas")))
;(sh "open" "http://localhost:3000")
