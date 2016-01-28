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
(def remote (node/require "remote"))

(def robip-server-uri "http://127.0.0.1:3000")

#_(def port-name "/dev/tty.usbserial-DA01LW3C")

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

(defn gen-code [workspace]
  (.workspaceToCode Arduino workspace))

(r/register-handler
 :init
 (fn [_ _]
   {:build-progress :done
    :view :block
    :edit {}}))

(r/register-handler
 :report-error
 [r/trim-v]
 (fn [db [msg]]
   (error msg)
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
                                       (.. elem -offsetTop))]
                         (set! (.. elem -style -height) (str height "px"))))
         resize-editor #(do (adjust-size (.getElementById js/document "blockly"))
                            (adjust-size (.getElementById js/document "text-editor")))]
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
     (api-request "/api/build"
                  (fn [[ok? res]]
                    (if (and ok? (= (:status res) "ok"))
                      (r/dispatch [:download-binary (:url res)])
                      (let [message (cond-> "build failed"
                                      (:err res) (str "\n" (:err res)))]
                        (r/dispatch [:report-error message]))))
                  :method :post
                  :params {:code code})
     (assoc db :build-progress :building))))

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
         proc (->> #js["-jar" lib-path "--default-port" "0" file-path]
                   (cp.spawn "java"))
         err (atom "")]
     (.on proc "exit"
          (fn [code signal]
            (if (= code 0)
              (r/dispatch [:upload-complete])
              (r/dispatch [:report-error (str "uploading failed\n" @err)]))))
     (.on (.-stderr proc) "data"
          (fn [data] (swap! err str data))))
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

(r/register-sub
 :view
 (fn [db _]
   (reaction (:view @db))))

(r/register-sub
 :edit
 (fn [db _]
   (reaction (:edit @db))))

(defn header []
  (let [build-progress (r/subscribe [:build-progress])
        workspace (r/subscribe [:workspace])
        button (fn [attrs body]
                 [:button (merge {:type "button"
                                  :class "build-button pure-button"
                                  :title "アップロード"}
                                 attrs)
                  body])]
    (fn []
      (let [content [:i {:class "fa fa-arrow-circle-right"}]]
       [:div.pure-u-1
        [:div.header
         (if (= @build-progress :done)
           (button {:on-click (fn [e] (r/dispatch [:build]))} content)
           (button {:disabled true} content))]]))))

(defn view-selector []
  (let [view (r/subscribe [:view])
        edit (r/subscribe [:edit])
        cond-pure-class #(if %2 (str %1 " pure-menu-" %3) %1)
        view-selector #(fn [e] (r/dispatch [:select-view %]))
        wrap-link (fn [view text]
                    [:a.pure-menu-link {:on-click (view-selector view)}
                     text])]
    (fn []
      [:div.pure-u-1
       [:div.pure-menu.pure-menu-horizontal
        [:ul.pure-menu-list
         [:li {:class (-> "pure-menu-item"
                          (cond-pure-class (= @view :block) "selected")
                          (cond-pure-class (:editing? @edit) "disabled"))}
          (cond->> "ブロック"
            (not (:editing? @edit)) (wrap-link :block))]
         [:li {:class (-> "pure-menu-item"
                          (cond-pure-class (= @view :code) "selected"))}
          (wrap-link :code "コード")]]]])))

(def text-editor
  (let [edit (r/subscribe [:edit])
        update-code (fn [e]
                      (let [modified-code (.. e -target -value)
                            caret (.. e -target -selectionStart)]
                        (r/dispatch [:update-code modified-code caret])))]
    (with-meta
      (fn []
        [:textarea#text-editor.pure-input-1
         {:on-change (fn [e]
                       (if-not (:editing? @edit)
                         (and (js/confirm "コードを編集するとブロックでの操作ができなくなります。本当に編集しますか？")
                              (update-code e))
                         (update-code e)))
          :value (:code @edit)}])
      {:component-did-update (fn [this _ _]
                               (when-let [caret (:caret @edit)]
                                 (-> (reagent/dom-node this)
                                     (.setSelectionRange caret caret))))})))

(def editor
  (let [view (r/subscribe [:view])
        view-display #(array-map :display (if (= @view %1):block :none))]
    (with-meta
      (fn []
        [:div.pure-u-1
         [:div#blockly.pure-u-1 {:style (view-display :block)}]
         [:form.pure-form {:style (view-display :code)}
          [text-editor]]])
      {:component-did-mount (fn [_]
                              (r/dispatch [:after-editor-mount @view]))
       :component-did-update (fn [_ _ _]
                               (r/dispatch [:after-editor-update @view]))})))

(defn app []
  [:div.pure-g
   [header]
   [view-selector]
   [editor]])

(defn ^:export main []
  (r/dispatch-sync [:init])
  (reagent/render [app] (.getElementById js/document "app")))

(set! (.-onload js/window) main)
