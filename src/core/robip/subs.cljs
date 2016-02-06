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

(r/register-sub
 :logs
 (fn [db _]
   (reaction (:logs @db))))

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
