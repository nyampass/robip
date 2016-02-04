(ns robip.handlers.util
  (:require [re-frame.core :as r]))

(defn log [msg]
  (r/dispatch [::log msg]))

(defn error [msg]
  (r/dispatch [::error msg]))

(r/register-handler
 ::log
 [r/trim-v]
 (fn [db [msg]]
   (update db ::logs str msg "\n")))

(r/register-handler
 ::error
 [r/trim-v]
 (fn [db [msg]]
   (log (str "エラー：" msg))
   (if (not= (:build-progress db) :done)
     (assoc db :build-progress :done)
     db)))
