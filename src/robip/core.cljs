(ns robip.core
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as r]
            [goog.events :as events]
            [ajax.core :as ajax]
            Blockly
            Blockly.Variables
            Blockly.inject
            Blockly.utils
            Blockly.Blocks.base
            Blockly.Blocks.colour
            Blockly.Blocks.grove
            Blockly.Blocks.lists
            Blockly.Blocks.logic
            Blockly.Blocks.loops
            Blockly.Blocks.math
            Blockly.Blocks.procedures
            Blockly.Blocks.texts
            Blockly.Blocks.variables)
  (:import [goog History]
           [goog.history EventType]
           Blockly.Arduino
           Blockly.Msg))

(enable-console-print!)

(r/register-handler
 :init
 (fn [_ _]
   (let [workspace (Blockly.inject "blocklyDiv"
                           #js{:toolbox (.getElementById js/document "toolbox")})]
     workspace)))

(defn ^:export main []
  (println Blockly.Variables/NAME_TYPE)
  (r/dispatch-sync [:init])
  #_(reagent/render [menubar] (.getElementById js/document "app")))

(set! (.-onload js/window) main)
