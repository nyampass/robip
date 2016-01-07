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

(defn error [msg]
  (js/alert (str "ERROR: " msg)))

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
     {:workspace workspace :build-progress :done})))

(r/register-handler
 :report-error
 [r/trim-v]
 (fn [db [msg]]
   (error msg)
   (if (not= (:build-progress db) :done)
     (assoc db :build-progress :done)
     db)))

(r/register-handler
 :build
 [r/trim-v]
 (fn [db [code]]
   (api-request "/api/build"
                (fn [[ok? result]]
                  (if (and ok? (= (:status result) "ok"))
                    (r/dispatch [:download-binary (:url result)])
                    (let [message (cond-> "build failed"
                                    (:err result) (str "\n" (:err result)))]
                      (r/dispatch [:report-error message]))))
                :method :post
                :params {:code code})
   (assoc db :build-progress :building)))

(r/register-handler
 :download-binary
 [r/trim-v]
 (fn [db [path]]
   (request (str robip-server-uri path)
            #js{:encoding nil}
            (fn [err res body]
              (if-not err
                (r/dispatch [:write-to-file body])
                (r/dispatch [:report-error "build failed"]))))
   (assoc db :build-progress :downloading)))

(r/register-handler
 :write-to-file
 [r/trim-v]
 (fn [db [content]]
   (let [path (-> (os.tmpdir) (path.join "firmware.bin"))]
     (fs.writeFile path content
                   (fn [err]
                     (if-not err
                       (r/dispatch [:upload-to-device path])
                       (r/dispatch [:report-error "build failed"])))))
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
              (r/dispatch [:upload-complete])
              (r/dispatch [:report-error "uploading failed"]))))
     (.on (.-stderr proc) "data"
          (fn [data] (println (str data)))))
   (assoc db :build-progress :uploading)))

(r/register-handler
 :upload-complete
 (fn [db _]
   (println "upload completed")
   (assoc db :build-progress :done)))

(r/register-sub
 :workspace
 (fn [db _]
   (reaction (:workspace @db))))

(r/register-sub
 :build-progress
 (fn [db _]
   (reaction (:build-progress @db))))

(defn buttons []
  (let [build-progress (r/subscribe [:build-progress])
        workspace (r/subscribe [:workspace])
        codegen #(.workspaceToCode Arduino @workspace)]
    (fn []
      [:div
       [:button {:type "button"
                 :on-click (fn [e]
                             ;; TODO: rewrite code without direct DOM handling
                             (set! (.-value (.getElementById js/document
                                                             "blocklyCode"))
                                   (codegen)))}
        "codegen"]
       (if (= @build-progress :done)
         [:button {:type "button"
                   :on-click (fn [e] (r/dispatch [:build (codegen)]))}
          "build"]
         [:button {:type "button" :disabled true}
          (str (name @build-progress) " ...")])])))

(defn ^:export main []
  (r/dispatch-sync [:init])
  (reagent/render [buttons] (.getElementById js/document "buttons")))

(set! (.-onload js/window) main)
