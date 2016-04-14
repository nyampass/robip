(ns robip.handlers.core
  (:require [re-frame.core :as r]
            [ajax.core :as ajax]
            robip.blockly
            Blockly.inject
            Blockly.Xml
            [robip.handlers.util :as util]
            [robip.settings :as settings]))

(defn header-height [db]
  (if (:app-mode? db) 50 50))

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

(defn fetch-user-info []
  (api-request "/api/users/me"
               (fn [[ok? res]]
                 (when ok?
                   (r/dispatch [:update-setting :wifi (-> res :user :wifi)])
                   (r/dispatch [:update-setting :robip-id (-> res :user :robip-id)])
                   (r/dispatch [:update-login-state (-> res :user :id) (-> res :user :name)])))))

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
   (set! (.-show-log js/window) show-log)
   (set! (.-clear-blockly js/window) clear-blockly)
   (fetch-user-info)
   (let [app-mode? (boolean (re-seq #"app.html" (.-pathname (.-location js/window))))]
     {:settings {:wifi []}
      :app-mode? app-mode?
      :build-progress :done
      :view :block
      :edit {}
      :logs ""
      :login {}})))

(r/register-handler
 :initialize-app
 (fn [db _]
   (if (:app-mode? db)
     (try
       (.init js/appBridge)
       (catch js/Error _)))
   db))

(r/register-handler
 :toggle-settings-pane
 [r/trim-v]
 (fn [db [shown?]]
   (if (:app-mode? db)
     (do
       (try
         (.showMenu js/appBridge)
         (catch js/Error _))
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


(defn send-wifi-settings [db]
  (api-request "/api/users/me/wifi"
               (fn [[ok? res]])
               :method :post
               :params {:wifi (-> db :settings :wifi)}))

(r/register-handler
 :update-setting
 [r/trim-v]
 (fn [db [field-name content]]
   (let [db (assoc-in db [:settings field-name] content)]
     (condp = field-name
       :robip-id
       (api-request "/api/users/me/robip-id"
                    (fn [[ok? res]])
                    :method :post
                    :params {:robip-id content})
       (send-wifi-settings db))
     db)))

(r/register-handler
 :update-wifi-setting
 [r/trim-v]
 (fn [db [key index content]]
   (prn :update-wifi-setting (-> db :settings :wifi) index content key)
   (let [wifi (assoc-in (vec (-> db :settings :wifi)) [index key] content)
         db (assoc-in db [:settings :wifi] wifi)]
     (send-wifi-settings db)
     db)))

(r/register-handler
 :append-wifi-setting
 [r/trim-v]
 (fn [{{wifi :wifi} :settings :as db} []]
   (assoc-in db [:settings :wifi] (cons {:ssid "" :password ""} wifi))))

(defn vec-remove
  [coll pos]
  (vec (concat (subvec coll 0 pos) (subvec coll (inc pos)))))

(r/register-handler
 :remove-wifi-setting
 [r/trim-v]
 (fn [{{wifi :wifi} :settings :as db} [index]]
   (let [db (assoc-in db [:settings :wifi] (vec-remove wifi index))]
     (send-wifi-settings db)
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

(defn clear-blockly []
  (.clear Blockly.mainWorkspace)
  (let [xml (Blockly.Xml.textToDom initial-blocks-xml)]
    (Blockly.Xml.domToWorkspace Blockly.mainWorkspace xml)))

(r/register-handler
 :after-editor-mount
 [r/trim-v]
 (fn [db [view]]
   (let [opts #js{:toolbox (.getElementById js/document "toolbox")
                  :zoom {:wheel false
                         :startScale 1.0
                         :maxScale 1.0 :minScale 1.0}
                  :trashcan false}
         workspace (Blockly.inject "blockly" opts)
         adjust-size (fn [elem]
                       (let [height (- (.-innerHeight js/window)
                                       (header-height db))]
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
     (.modal (js/jQuery "#logging-area"))
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
                           :wifi (map #(dissoc % :index) (-> db :settings :wifi))})
     (assoc db :build-progress :building))))

(r/register-handler
 :build-complete
 (fn [db _]
   (util/log "ビルドが完了しました")
   (js/alert "ビルドが完了しました.HaLakeボードの電源を入れなおして、30秒ほどお待ち下さい")
   (assoc db :build-progress :done)))

(r/register-handler
 :exit
 (fn [db _]
   (settings/save-to-local-storage (:settings db))
   db))

(r/register-handler
 :signup
 [r/trim-v]
 (fn [db [{:keys [email name password re-password] :as form} dialog-show?]]
   (cond
     (not (seq email)) (js/alert "メールアドレスを入力してください")
     (not (seq name)) (js/alert "ユーザ名を入力してください")
     (not (seq password)) (js/alert "パスワードを入力してください")
     (not= password re-password)　(js/alert "確認用のパスワードとパスワードを一致させてください")
     :else
     (api-request "/api/users"
                  (fn [[ok? res]]
                    (js/alert (:message res))
                    (when (and ok?
                               (= (:status res) "ok"))
                      (reset! dialog-show? false)
                      (r/dispatch [:login email password])))
                  :method :post
                  :params form))
   db))

(r/register-handler
  :login
  [r/trim-v]
  (fn [db [email password dialog-show?]]
     (api-request "/api/login"
                  (fn [[ok? res]]
                    (if (and ok?
                             (= (:status res) "ok"))
                      (do
                        (r/dispatch [:update-login-state (:id res) (:name res)])
                        (if dialog-show?
                          (reset! dialog-show? false)))
                      (js/alert "ログインできませんでした。メールアドレスとパスワードを確認してください")))
                  :method :post
                  :params {:email email :password password})
    db))

(r/register-handler
 :update-login-state
 [r/trim-v]
 (fn [db [id name]]
   (assoc db :login {:id id :name name})))

(r/register-handler
  :logout
  [r/trim-v]
  (fn [db _]
    (api-request (str "/api/logout")
                 (fn [[ok? res]]))
    (dissoc db :login {})))
