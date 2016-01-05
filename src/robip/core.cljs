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
(def request (node/require "request"))
(def os (node/require "os"))
(def fs (node/require "fs"))
(def path (node/require "path"))

(def robip-server-uri "http://127.0.0.1:3000")

(def port-name "/dev/tty.usbserial-DA01LW3C")

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
     (set! (.-onclick (.getElementById js/document "build"))
           (fn [e]
             (let [code (.workspaceToCode Arduino workspace)]
               (r/dispatch [:build code]))))
     workspace)))

(r/register-handler
 :build
 [r/trim-v]
 (fn [db [code]]
   (api-request "/api/build"
                (fn [[ok? result]]
                  (if ok?
                    (r/dispatch [:download-binary (:url result)])))
                :method :post
                :params {:code code})
   db))

(r/register-handler
 :download-binary
 [r/trim-v]
 (fn [db [path]]
   (request (str robip-server-uri path)
            #js{:encoding nil}
            (fn [err res body]
              (when-not err
                (r/dispatch [:write-to-file body]))))
   db))

(r/register-handler
 :write-to-file
 [r/trim-v]
 (fn [db [content]]
   (let [path (-> (os.tmpdir) (path.join "firmware.bin"))]
     (fs.writeFile path content
                   (fn [err]
                     (when-not err (r/dispatch [:upload-to-device path])))))
   db))

(r/register-handler
 :upload-to-device
 [r/trim-v]
 (fn [db [file-path]]
   (let [lib-path (path.join "lib" "robip-tool" "robip-tool.jar")
         proc (->> #js["-jar" lib-path "-p" port-name "0" file-path]
                   (cp.spawn "java"))]
     (.on proc "exit"
          (fn [code signal]
            (if (= code 0)
              (r/dispatch [:upload-complete]))))
     (.on (.-stderr proc) "data"
          (fn [data] (println (str data)))))
   db))

(r/register-handler
 :upload-complete
 (fn [db _]
   (println "upload completed")
   db))

(defn ^:export main []
  (r/dispatch-sync [:init])
  #_(reagent/render [menubar] (.getElementById js/document "app")))

(set! (.-onload js/window) main)
