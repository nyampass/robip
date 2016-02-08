(ns robip.settings
  (:require [cljs.reader :as reader]))

(defn load-from-local-storage []
  (when (.-localStorage js/window)
    (when-let [settings (.getItem (.-localStorage js/window) "settings")]
      (reader/read-string settings))))

(defn save-to-local-storage [settings]
  (when (.-localStorage js/window)
    (let [settings-str (str settings)]
      (.setItem (.-localStorage js/window) "settings" settings-str))))
