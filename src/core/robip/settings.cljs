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

(defn ssid-key->index [key]
  (if-let [matched (re-seq #"ssid-(\d+)" (name key))]
    (-> matched first second js/parseInt)))

(defn index->ssid-key [index]
  (keyword (str "ssid-" index)))

(defn index->password-key [index]
  (keyword (str "password-" index)))

(defn wifi-settings [{{wifi :wifi} :settings :as db}]
  (->> wifi
       (keep (fn [[k v]]
               (if-let [index (ssid-key->index k)]
                 (do
                 {:index index
                  :ssid v
                  :password (get wifi (index->password-key index))}))))
      (sort-by :index)))
