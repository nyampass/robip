(ns robip.core
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as r]
            [goog.events :as events]
            [ajax.core :as ajax]
            [cljs.nodejs :as node]
            robip.blockly
            Blockly.inject)
  (:import [goog History]
           [goog.history EventType]
           Blockly.Arduino))

(def child-process (node/require "child_process"))

(enable-console-print!)

(r/register-handler
 :init
 (fn [_ _]
   (let [opts #js{:toolbox (.getElementById js/document "toolbox")}
         workspace (Blockly.inject "blocklyDiv" opts)
         button (.getElementById js/document "codeGen")]
     (set! (.-onclick button)
           (fn [e]
             (let [code-area (.getElementById js/document "blocklyCode")
                   code (.workspaceToCode Arduino workspace)]
               (set! (.-value code-area) code))))
     (let [proc (.spawn child-process "java" #js["-version"])]
       (.on (.-stderr proc)
            "data"
            (fn [data] (println (str data)))))
     workspace)))

(defn ^:export main []
  (r/dispatch-sync [:init])
  #_(reagent/render [menubar] (.getElementById js/document "app")))

(set! (.-onload js/window) main)
