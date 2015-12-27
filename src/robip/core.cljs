(ns robip.core
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as r]
            [goog.events :as events]
            [ajax.core :as ajax]
            robip.blockly
            Blockly.inject)
  (:import [goog History]
           [goog.history EventType]
           Blockly.Arduino))

(enable-console-print!)

(r/register-handler
 :init
 (fn [_ _]
   (let [workspace (Blockly.inject "blocklyDiv"
                           #js{:toolbox (.getElementById js/document "toolbox")})]
     workspace)))

(defn ^:export main []
  (r/dispatch-sync [:init])
  #_(reagent/render [menubar] (.getElementById js/document "app")))

(set! (.-onload js/window) main)
