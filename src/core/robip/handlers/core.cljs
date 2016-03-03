(ns robip.handlers.core
  (:require [re-frame.core :as r]
            [ajax.core :as ajax]
            robip.blockly
            Blockly.inject
            Blockly.Xml
            [robip.handlers.util :as util]
            [robip.settings :as settings]))

(defn header-height [db]
  (if (:app-mode? db) 0 40))

(defn logging-area-height [db]
  (if (:app-mode? db) 0 150))

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

(defn fetch-wifi-settings [robip-id]
  (r/dispatch [:update-setting :robip-id robip-id])
  (api-request (str "/api/" robip-id "/wifi")
               (fn [[ok? res]]
                 (if ok?
                   (do
                     (js/alert (str "HaLakeボードの接続情報を読み込みました. (Robip ID: " robip-id ")"))
                     (r/dispatch [:update-all-wifi-setting (:wifi res)]))
                   (js/alert (str "HaLakeボードの接続情報に失敗しました. (Robip ID: " robip-id ")"))))))

(defn show-log [robip-id]
  (api-request (str "/api/" robip-id "/logs")
               (fn [[ok? res]]
                 (js/alert (if ok? (:logs res)
                               "取得に失敗しました")))))

(defn gen-code [workspace]
  (.workspaceToCode Blockly.Arduino workspace))

(r/register-handler
 :init
 (fn [_ _]
   (set! (.-fetch-api-settings js/window) fetch-wifi-settings)
   (set! (.-show-log js/window) show-log)
   (set! (.-clear-blockly js/window) clear-blockly)
   (let [app-mode? (boolean (re-seq #"app.html" (.-pathname (.-location js/window))))
         settings (settings/load-from-local-storage)]
     {:settings (or settings {})
      :app-mode? app-mode?
      :build-progress :done
      :view :block
      :edit {}
      :logs ""})))

(r/register-handler
 :initialize-app
 (fn [db _]
   (if (:app-mode? db)
       (.init js/appBridge))
   db))

(r/register-handler
 :after-logging
 [r/trim-v]
 (fn [db [elem]]
   (if-not (:app-mode? db)
     (set! (.-scrollTop elem) (.-scrollHeight elem)))
   db))

(r/register-handler
 :toggle-settings-pane
 [r/trim-v]
 (fn [db [shown?]]
   (if (:app-mode? db)
     (do
       (.showMenu js/appBridge)
       db)
     (let [db (if-not (nil? shown?)
                (assoc db :settings-pane-shown? shown?)
                (update db :settings-pane-shown? not))]
       db))))

(r/register-handler
 :toggle-settings-pane-web
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
 :update-all-wifi-setting
 [r/trim-v]
 (fn [db [wifi-settings]]
   (let [wifi-settings (apply merge 
                              (keep-indexed
                               (fn [i {:keys [ssid password]}]
                                 {(keyword (str "ssid-" (inc i))) ssid
                                  (keyword (str "password-" (inc i))) password}) wifi-settings))]
     (prn :update-all-wifi-setting wifi-settings)
     (assoc-in db [:settings :wifi] wifi-settings))))

(r/register-handler
 :update-wifi-setting
 [r/trim-v]
 (fn [db [key index content]]
   (assoc-in db [:settings :wifi (keyword (str (name key) "-" index))] content)))

(r/register-handler
 :append-wifi-setting
 [r/trim-v]
 (fn [{{wifi :wifi} :settings :as db} []]
   (let [new-index (inc (reduce max 0 (keep #(some->> %
                                                      first name
                                                      (re-seq #"ssid-(\d+)")
                                                      first second
                                                      js/parseInt)
                                          wifi)))]
     (-> db
         (assoc-in [:settings :wifi (keyword (str "ssid-" new-index))] "")
         (assoc-in [:settings :wifi (keyword (str "password-" new-index))] "")))))

(r/register-handler
 :remove-wifi-setting
 [r/trim-v]
 (fn [{{wifi :wifi} :settings :as db} [index]]
   (prn :remove-wifi-settings index)
   (assoc-in db
             [:settings :wifi]
             (-> wifi
                 (dissoc (keyword (str "ssid-" index)))
                 (dissoc (keyword (str "password-" index)))))))

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

(defn clear-blockly []
  (.clear Blockly.mainWorkspace)
  (let [xml (Blockly.Xml.textToDom initial-blocks-xml)]
    (Blockly.Xml.domToWorkspace Blockly.mainWorkspace xml)))

(r/register-handler
 :after-editor-mount
 [r/trim-v]
 (fn [db [view]]
   (let [opts #js{:toolbox (.getElementById js/document "toolbox")}
         workspace (Blockly.inject "blockly" opts)
         adjust-size (fn [elem]
                       (let [height (- (.-innerHeight js/window)
                                       (header-height db)
                                       (logging-area-height db))]
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
   (let [{:keys [robip-id wifi]} (:settings db)
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
                           :wifi (map #(dissoc % :index) (settings/wifi-settings db))})
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
