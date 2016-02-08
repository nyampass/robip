(ns robip.handlers.core
  (:require [re-frame.core :as r]
            [ajax.core :as ajax]
            robip.blockly
            Blockly.inject
            Blockly.Xml
            [robip.handlers.util :as util]
            [robip.settings :as settings]))

(def id "hogehoge")

(def robip-server-uri "http://127.0.0.1:3000")

(def header-height 40)
(def logging-area-height 150)

(defn api-request [path callback & opts]
  (let [{:keys [method params format] :or {method :get}} opts
        format (case format
                 (:json nil) (ajax/json-response-format {:keywords? true})
                 :raw (ajax/raw-response-format)
                 (assert false "format must be :json or :raw"))
        request (cond-> {:uri path
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
   (let [settings (settings/load-from-local-storage)]
     {:settings (or settings {})
      :build-progress :done
      :view :block
      :edit {}
      :logs ""})))

(r/register-handler
 :after-logging
 [r/trim-v]
 (fn [db [elem]]
   (set! (.-scrollTop elem) (.-scrollHeight elem))
   db))

(r/register-handler
 :toggle-settings-pane
 [r/trim-v]
 (fn [db [shown?]]
   (if-not (nil? shown?)
     (assoc db :settings-pane-shown? shown?)
     (update db :settings-pane-shown? not))))

(r/register-handler
 :update-setting
 [r/trim-v]
 (fn [db [field-name content]]
   (assoc-in db [:settings field-name] content)))

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
                       (let [height (- (.-innerHeight js/window)
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
   (let [{:keys [robip-id wifi-ssid wifi-password]} (:settings db)
         {:keys [code editing?]} (:edit db)
         code (if editing?
                code
                (gen-code (:workspace db)))]
     (util/log "ビルド中...")
     (api-request (str "/api/" robip-id "/build")
                  (fn [[ok? res]]
                    (if (and ok? (= (:status res) "ok"))
                      (r/dispatch [:build-complete])
                      (let [message (cond-> "ビルドに失敗しました"
                                      (:err res) (str "\n" (:err res)))]
                        (util/error message))))
                  :method :post
                  :params {:code code
                           :ssid wifi-ssid
                           :pass wifi-password})
     (assoc db :build-progress :building))))

(r/register-handler
 :build-complete
 (fn [db _]
   (util/log "ビルドが完了しました")
   (assoc db :build-progress :done)))

(r/register-handler
 :exit
 (fn [db _]
   (settings/save-to-local-storage (:settings db))
   db))
