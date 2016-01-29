(ns robip.handlers
  (:require [re-frame.core :as r]
            [ajax.core :as ajax]
            [cljs.nodejs :as node]
            robip.blockly
            Blockly.inject
            Blockly.Xml))

(def cp (node/require "child_process"))
(def request (node/require "request"))
(def os (node/require "os"))
(def fs (node/require "fs"))
(def path (node/require "path"))
(def remote (node/require "remote"))

(def robip-server-uri "http://127.0.0.1:3000")

(def header-height 75)
(def logging-area-height 150)

(defn log [msg]
  (r/dispatch [:log msg]))

(defn error [msg]
  (r/dispatch [:error msg]))

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

(defn gen-code [workspace]
  (.workspaceToCode Blockly.Arduino workspace))

(r/register-handler
 :init
 (fn [_ _]
   {:build-progress :done
    :view :block
    :edit {}
    :logs ""}))

(r/register-handler
 :log
 [r/trim-v]
 (fn [db [msg]]
   (update db :logs str msg "\n")))

(r/register-handler
 :error
 [r/trim-v]
 (fn [db [msg]]
   (log (str "エラー：" msg))
   (if (not= (:build-progress db) :done)
     (assoc db :build-progress :done)
     db)))

(r/register-handler
 :select-view
 [r/trim-v]
 (fn [db [view]]
   (let [db' (assoc db :view view)]
     (if (= view :code)
       (update db' :edit assoc :code (gen-code (:workspace db')) :caret 0)
       db'))))

(r/register-handler
 :update-code
 [r/trim-v]
 (fn [db [code caret]]
   (update db :edit assoc :editing? true :code code :caret caret)))

(def initial-blocks-xml
  (str "<xml xmlns=\"http://www.w3.org/1999/xhtml\">"
       "<block type=\"entry_point\" id=\"0\" x=\"30\" y=\"20\"></block>"
       "</xml>"))

(r/register-handler
 :after-editor-mount
 [r/trim-v]
 (fn [db [view]]
   (let [opts #js{:toolbox (.getElementById js/document "toolbox")}
         workspace (Blockly.inject "blockly" opts)
         adjust-size (fn [elem]
                       (let [height (- (.. remote
                                           getCurrentWindow
                                           getBounds
                                           -height)
                                       header-height
                                       logging-area-height)]
                         (set! (.. elem -style -height) (str height "px"))))
         resize-editor #(do (adjust-size (.getElementById js/document "blockly"))
                            (adjust-size (.getElementById js/document "text-editor")))
         xml (Blockly.Xml.textToDom initial-blocks-xml)]
     (Blockly.Xml.domToWorkspace workspace xml)
     (set! (.-onresize js/window)
           (fn [e] (resize-editor)))
     (resize-editor)
     (assoc db :workspace workspace))))

(r/register-handler
 :after-editor-update
 [r/trim-v]
 (fn [db [view]]
   (let [div (-> js/document
                 (.getElementsByClassName "blocklyToolboxDiv")
                 array-seq
                 first)]
     (set! (.. div -style -display)
           (if (= view :block) "block" "none")))
   db))

(r/register-handler
 :build
 (fn [db _]
   (let [{:keys [code editing?]} (:edit db)
         code (if editing?
                code
                (gen-code (:workspace db)))]
     (log "ビルド中...")
     (api-request "/api/build"
                  (fn [[ok? res]]
                    (if (and ok? (= (:status res) "ok"))
                      (r/dispatch [:download-binary (:url res)])
                      (let [message (cond-> "ビルドに失敗しました"
                                      (:err res) (str "\n" (:err res)))]
                        (error message))))
                  :method :post
                  :params {:code code})
     (assoc db :build-progress :building))))

(r/register-handler
 :download-binary
 [r/trim-v]
 (fn [db [path]]
   (log "ダウンロード中...")
   (request (str robip-server-uri path)
            #js{:encoding nil}
            (fn [err res body]
              (if-not err
                (r/dispatch [:write-to-file body])
                (error "ビルドに失敗しました"))))
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
                       (error "ビルドに失敗しました")))))
   db))

(r/register-handler
 :upload-to-device
 [r/trim-v]
 (fn [db [file-path]]
   (log "書き込み中...")
   (let [lib-path (path.join "lib" "robip-tool" "robip-tool.jar")
         proc (->> #js["-jar" lib-path "--default-port" "0" file-path]
                   (cp.spawn "java"))
         err (atom "")]
     (.on proc "exit"
          (fn [code signal]
            (if (= code 0)
              (r/dispatch [:upload-complete])
              (error (str "書き込みに失敗しました\n" @err)))))
     (.on (.-stderr proc) "data"
          (fn [data] (swap! err str data))))
   (assoc db :build-progress :uploading)))

(r/register-handler
 :upload-complete
 (fn [db _]
   (log "書き込みが完了しました")
   (assoc db :build-progress :done)))
