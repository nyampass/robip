(ns robip.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as r]))

(r/register-sub
 :workspace
 (fn [db _]
   (reaction (:workspace @db))))

(r/register-sub
 :settings
 (fn [db [_ field-name]]
   (reaction (get-in @db [:settings field-name]))))

(defn ssid-key->index [key]
  (if-let [matched (re-seq #"ssid-(\d+)" (name key))]
    (-> matched first second js/parseInt)))

(defn index->ssid-key [index]
  (keyword (str "ssid-" index)))

(defn index->password-key [index]
  (keyword (str "password-" index)))

(defn wifi-settings [{{wifi :wifi} :settings :as db}]
  (prn :wifi-settings wifi)
  (->> wifi
       (keep (fn [[k v]]
               (if-let [index (ssid-key->index k)]
                 (do
                 {:index index
                  :ssid v
                  :password (get wifi (index->password-key index))}))))
      (sort-by :index)))

(r/register-sub
 :wifi-settings
 (fn [db [_ & [index]]]
   (if index
     (reaction (let [settings (wifi-settings @db)]
                 (if (< index (count settings))
                   (nth settings index)
                   {:ssid "" :password ""})))
     (reaction (wifi-settings @db)))))

(r/register-sub
 :logs
 (fn [db _]
   (reaction (:logs @db))))

(r/register-sub
 :settings-pane-shown?
 (fn [db _]
   (reaction (:settings-pane-shown? @db))))

(r/register-sub
 :build-progress
 (fn [db _]
   (reaction (:build-progress @db))))

(r/register-sub
 :view
 (fn [db _]
   (reaction (:view @db))))

(r/register-sub
 :edit
 (fn [db _]
   (reaction (:edit @db))))
