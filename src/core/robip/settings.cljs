(ns robip.settings)

(defn ssid-key->index [key]
  (if-let [matched (re-seq #"ssid-(\d+)" (name key))]
    (-> matched first second js/parseInt)))

(defn index->ssid-key [index]
  (keyword (str "ssid-" index)))

(defn index->password-key [index]
  (keyword (str "password-" index)))

(defn wifi-settings [{{wifi :wifi} :settings :as db}]
  (prn :wifi-settings wifi)
  wifi)
