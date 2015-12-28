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

(def cp (node/require "child_process"))
(def os (node/require "os"))
(def fs (node/require "fs"))
(def path (node/require "path"))

(def robip-server-uri "http://127.0.0.1:3000")

(enable-console-print!)

(defn api-request [path callback & opts]
  (let [{:keys [method params format] :or {method :get}} opts
        format (case format
                 (:json nil) (ajax/json-response-format {:keywords? true})
                 :raw (ajax/raw-response-format)
                 (assert false "format must be :json or :raw"))
        request (cond-> {:uri (str robip-server-uri path)
                         :method method
                         :handler callback
                         :response-format format}
                  params (assoc :params params
                                :format (ajax/json-request-format)))]
    (ajax/ajax-request request)))

(r/register-handler
 :init
 (fn [_ _]
   (let [opts #js{:toolbox (.getElementById js/document "toolbox")}
         workspace (Blockly.inject "blocklyDiv" opts)]
     (set! (.-onclick (.getElementById js/document "codegen"))
           (fn [e]
             (let [code-area (.getElementById js/document "blocklyCode")
                   code (.workspaceToCode Arduino workspace)]
               (set! (.-value code-area) code))))
     (letfn [(build [code]
               (api-request "/api/build" download
                            :method :post
                            :params {:code code}))
             (download [[ok? result]]
               (when ok?
                 (println "requesting" (:url result) "...")
                 (api-request (:url result) write-file :format :raw)))
             (write-file [[ok? result]]
               (when ok?
                 (println (type result))
                 (let [path (-> (os.tmpdir) (path.join "firmware.bin"))]
                   (println "writing binary content to" path)
                   (fs.writeFile path result update))))
             (update [err]
               (println "wrote up binary content to file!!"))]
       (set! (.-onclick (.getElementById js/document "build"))
             (fn [e] (build (.workspaceToCode Arduino workspace)))))
     (let [proc (cp.spawn "java" #js["-version"])]
       (.on (.-stderr proc)
            "data"
            (fn [data] (println (str data)))))
     workspace)))

(defn ^:export main []
  (r/dispatch-sync [:init])
  #_(reagent/render [menubar] (.getElementById js/document "app")))

(set! (.-onload js/window) main)
