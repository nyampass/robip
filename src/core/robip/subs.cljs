(ns robip.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [robip.settings :as settings]
            [re-frame.core :as r]))

(r/register-sub
 :workspace
 (fn [db _]
   (reaction (:workspace @db))))

(r/register-sub
 :settings
 (fn [db [_ field-name]]
   (reaction (get-in @db [:settings field-name]))))

(r/register-sub
 :wifi-settings
 (fn [db [_ & [index]]]
   (if index
     (reaction (let [settings (-> @db :settings :wifi)]
                 (if (< index (count settings))
                   (nth settings index)
                   ^{:key (gensym)} {:ssid "" :password ""})))
     (reaction (-> @db :settings :wifi)))))

(r/register-sub
 :logs
 (fn [db _]
   (reaction (:logs @db))))

(r/register-sub
 :server-logs
 (fn [db _]
   (reaction (:server-logs @db))))

(r/register-sub
 :settings-pane-shown?
 (fn [db _]
   (.log js/console :setttings-page-show? (:app-mode? db))
   (reaction (if (:app-mode? db)
               nil
               (:settings-pane-shown? @db)))))

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

(r/register-sub
 :login
 (fn [db _]
   (reaction (:login @db))))

(r/register-sub
 :current-file
 (fn [db _]
   (reaction (nth (:files @db) (:file-index @db)))))

(r/register-sub
 :files
 (fn [db _]
   (reaction (or (:files @db) []))))

(r/register-sub
 :app-mode?
 (fn [db _]
   (reaction (:app-mode? @db))))

(r/register-sub
 :authentication-mode
 (fn [db _]
   (reaction (:authentication-mode @db))))
