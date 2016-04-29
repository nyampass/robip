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

(defn update-info [user]
  (r/dispatch [:update-setting :wifi (-> user :wifi)])
  (r/dispatch [:update-setting :robip-id (-> user :robip-id)])
  (r/dispatch [:update-files (-> user :files)])
  (r/dispatch [:update-login-state (-> user :id) (-> user :name)])
  (r/dispatch [:load-file 0]))

(defn fetch-user-info []
  (api-request "/api/users/me"
               (fn [[ok? res]]
                 (when ok?
                   (update-info (-> res :user))))))
(r/register-handler
 :fetch-server-logs
 (fn [db _]
   (api-request (str "/api/board/logs")
                (fn [[ok? res]]
                  (if ok?
                    (r/dispatch [:update-server-logs (:logs res)])
                    (js/alert "取得に失敗しました"))))
   db))

(r/register-handler
 :update-server-logs
 [r/trim-v]
 (fn [db [logs]]
   (assoc db :server-logs (.split logs "\n"))))

(defn gen-code [workspace]
  (.workspaceToCode Blockly.Arduino workspace))

(r/register-handler
 :init
 (fn [_ _]
   (fetch-user-info)
   (js/setInterval #(r/dispatch [:auto-save]), 2000)
   (let [app-mode? (boolean (re-seq #"app.html" (.-pathname (.-location js/window))))]
     {:settings {:wifi [], :robip-id ""}
      :app-mode? app-mode?
      :build-progress :done
      :files [{:name "New file" :code ""}]
      :file-index 0
      :view :block
      :edit {}
      :logs ""
      :login {}
      :authentication-mode :login})))

(r/register-handler
 :initialize-app
 (fn [db _]
   db))

(r/register-handler
 :toggle-settings-pane
 [r/trim-v]
 (fn [db [shown?]]
   (.modal (js/jQuery "#settings-modal"))
   db))

(r/register-handler
 :change-authentication-mode
 [r/trim-v]
 (fn [db [mode]]
   (assoc db :authentication-mode mode)))

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
     (prn :update-setting   [:settings field-name] content)
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
   (assoc-in db [:settings :wifi] (conj (vec wifi)
                                        ^{:key (gensym)} {:ssid "" :password ""}))))

(defn vec-remove
  [coll pos]
  (vec (concat (subvec coll 0 pos) (subvec coll (inc pos)))))

(r/register-handler
 :remove-wifi-setting
 [r/trim-v]
 (fn [{{wifi :wifi} :settings :as db} [index]]
   (let [db (assoc-in db [:settings :wifi] (vec-remove (vec wifi) index))]
     (prn :remove-wifi-setting index (-> db :settings :wifi) (-> db :settings :wifi (nth 0) meta))
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

(defn clear-blockly
  ([] (clear-blockly initial-blocks-xml))
  ([xml-code]
   (prn :clear-blockly xml-code)
   (.clear Blockly.mainWorkspace)
   (let [xml (Blockly.Xml.textToDom xml-code)]
     (Blockly.Xml.domToWorkspace Blockly.mainWorkspace xml))))

(r/register-handler
 :after-editor-mount
 [r/trim-v]
 (fn [db [view]]
   (prn :after-editor-mount )
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
     (api-request (str "/api/board/build")
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
 :server-log
 (fn [db _]
   (r/dispatch [:fetch-server-logs])
   (.modal (js/jQuery "#server-log-modal"))
   db))

(r/register-handler
 :exit
 (fn [db _]
   ;; (settings/save-to-local-storage (:settings db))
   db))

(r/register-handler
 :signup
 [r/trim-v]
 (fn [db [{:keys [email name password re-password] :as form}]]
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
                      (r/dispatch [:change-authentication-mode :authorized])
                      (r/dispatch [:login email password])))
                  :method :post
                  :params form))
   db))

(r/register-handler
  :login
  [r/trim-v]
  (fn [db [email password]]
     (api-request "/api/login"
                  (fn [[ok? res]]
                    (if (and ok?
                             (= (:status res) "ok"))
                      (do
                        (update-info (:user res))
                        (r/dispatch [:change-authentication-mode :authorized]))
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
    (r/dispatch [:change-authentication-mode :login])
    (dissoc db :login {})))

(r/register-handler
 :new-file
 [r/trim-v]
 (fn [{files :files :as db} _]
   (if-let [filename (js/prompt "ファイル名を入力してください")]
     (let [filename (if (seq filename)
                      filename
                      "New File")
           files (conj files {:name filename, :xml nil})]
       (clear-blockly)
       (assoc db
              :files files
              :file-index (-> files count dec)
              :view :block
              :edit {}))
     db)))

(r/register-handler
 :load-file
 [r/trim-v]
 (fn [db [file-index]]
   (prn :load-file)
   (if-let [code (-> (:files db)
                     (nth file-index)
                     :xml)]
     (clear-blockly code))
   (assoc db
          :view :block
          :edit {}
          :file-index file-index)))

(defn blocky-xml []
  (Blockly.Xml.domToText (Blockly.Xml.workspaceToDom Blockly.mainWorkspace)))

(r/register-handler
 :save-file
 [r/trim-v]
 (fn [db _]
   (let [xml (blocky-xml)
         files (-> db :files vec)
         db (assoc db :files files)
         file {:name (-> (:files db) (nth (:file-index db)) :name)
               :xml xml}]
     (api-request (str "/api/users/me/files/" (:file-index db))
                  (fn [[ok? res]])
                  :method :put
                  :params file)
     (assoc-in db [:files (:file-index db)] file))))

(def latest-save-file (atom {:file-index -1
                             :xml nil}))

(r/register-handler
 :auto-save
 [r/trim-v]
 (fn [db _]
   (if (and (:file-index db)
            (:files db)
            (> (count (:files db)) (:file-index db)))
     (let [xml (blocky-xml)]
       (prn :auto-save
            (:file-index @latest-save-file) (:file-index db)
            (:xml @latest-save-file) xml
            (not= (:file-index @latest-save-file) (:file-index db))
            (not= (:xml @latest-save-file) xml))
       (if (or (not= (:file-index @latest-save-file) (:file-index db))
               (not= (:xml @latest-save-file) xml))
         (do
           (reset! latest-save-file {:file-index (:file-index db)
                                     :xml xml})
           (r/dispatch [:save-file])))))
   db))

(r/register-handler
 :update-files
 [r/trim-v]
 (fn [db [files]]
   (let [files (vals files)]
     (assoc db
            :files files))))

(r/register-handler
 :send-program-to-ap
 [r/trim-v]
 (fn [db [files]]
   (if (-> db :settings :robip-id)
     (try
       (.sendProgramToAccessPoint js/appBridge (-> db :settings :robip-id))
       (catch js/Error _))
     (js/alert "先に[設定]からRobip IDを設定してください"))
   db))

