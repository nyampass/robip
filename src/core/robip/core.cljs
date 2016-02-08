(ns robip.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [robip.views :as views]))

(enable-console-print!)

(defn ^:export main []
  (js/window.addEventListener "beforeunload"
                              (fn [e] (r/dispatch-sync [:exit])))
  (r/dispatch-sync [:init])
  (reagent/render [views/app] (.getElementById js/document "app")))

(set! (.-onload js/window) main)
