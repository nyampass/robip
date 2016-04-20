(ns robip.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [robip.authentication :as auth]
            robip.handlers.core
            [robip.settings :as settings]
            robip.subs))

(defn cond-class [classes cond class]
  (if cond
    (str classes " " class)
    classes))

(defn click-handler-attr []
  (if (.hasOwnProperty js/window "ontouchstart")
    :on-touch-start
    :on-click))

(defn wrap-link [callback & content]
  `[:a ~{(click-handler-attr) callback} ~@content])

(defn wrap-button [callback & content]
  `[:button.btn.btn-default
    ~{:type "button"
      (click-handler-attr) callback} ~@content])

(defn view-selector []
  (let [view (r/subscribe [:view])
        edit (r/subscribe [:edit])
        file (r/subscribe [:current-file])
        files (r/subscribe [:files])
        view-selector #(fn [e] (r/dispatch [:select-view %]))]
    (fn []
      [:ul.nav.navbar-nav
       [:li.dropdown
        [:a.dropdown-toggle {:href "#", :data-toggle "dropdown",
                             :role "button", :aria-haspopup "true", :aria-expanded "false"}
         (list ^{:key 0} [:i.fa.fa-cloud] (str " ファイル:" (:name @file)))
         [:span.caret]]
        [:ul.dropdown-menu
         (concat
          [^{:key :new} [:li (wrap-link (fn [e] (r/dispatch [:new-file]))
                                        '(^{:key 0} [:i.fa.fa-plus-circle] " 新規ファイル"))]
           ^{:key :save} [:li {:key :save-file} (wrap-link (fn [e] (r/dispatch [:save-file]))
                                                           '(^{:key 0} [:i.fa.fa-cloud-upload] " 保存する"))]
           [:li.divider {:role "separator"}]]
           (keep-indexed
            (fn [i file]
              [:li
               (wrap-link (fn [e]
                            (r/dispatch [:load-file i]))
                          (str "読み込み: " (:name file)))])
             @files))]]
       [:li {:class (-> (cond-class "" (= @view :block) "active")
                        (cond-class (:editing? @edit) "disabled"))
             :role "presentation"}
        (if (not (:editing? @edit))
          (wrap-link (view-selector :block)
                     '([:i.fa.fa-square] " ブロック"))
          [:p.navbar-text
           '([:i.fa.fa-th-large] " ブロック")])]
       [:li {:class (cond-class "" (= @view :code) "active")
             :role "presentation"}
        (wrap-link (view-selector :code)
                   [:i.fa.fa-pencil-square-o] " コード")]])))

(defn menu []
  (let [build-progress (r/subscribe [:build-progress])
        workspace (r/subscribe [:workspace])
        robip-id (r/subscribe [:settings :robip-id])
        edit (r/subscribe [:edit])]
    (fn []
      [:ul.nav.navbar-nav.navbar-right.nav-pills
       (list
        ^{:key :signup}
        [:li
         [auth/signup]]
        ^{:key login}
        [:li
         [auth/login]]
        (let [disabled? (or (not= @build-progress :done) (empty? @robip-id))]
          ^{:key :build}
          [:li#build-menu
           [:button
            (let [button-attrs
                  {:type "submit"
                   :class "btn navbar-btn btn-primary"}]
              (if disabled?
                (assoc button-attrs :disabled "disabled")
                (assoc button-attrs (click-handler-attr)
                       (fn [e] (r/dispatch [:build])))))
            '([:i.fa.fa-paper-plane] [:b " ビルド"])]])
        ^{:key :setting}
       [:li.dropdown
        [:a.dropdown-toggle {:href "#", :data-toggle "dropdown",
                             :role "button", :aria-haspopup "true", :aria-expanded "false"}
         [:i.fa.fa-ellipsis-v]]
        [:ul.dropdown-menu
         [:li (wrap-link (fn [e] (r/dispatch [:server-log]))
                         '([:i.fa.fa-align-left] " ログを見る"))]
         [:li {:key :save-file} (wrap-link (fn [e] (r/dispatch [:toggle-settings-pane]))
                                           '(^{:key 0} [:i.fa.fa-gear] " 設定"))]]])])))

(defn header-menu []
  (fn []
    [:nav.navbar.navbar-default
     [:div.container-fluid
      [:div#navbar-collapse.collapse.navbar-collapse.off
       [view-selector]
       [menu]]]]))

(def text-editor
  (let [edit (r/subscribe [:edit])
        update-code (fn [e]
                      (let [modified-code (.. e -target -value)
                            caret (.. e -target -selectionStart)]
                        (r/dispatch [:update-code modified-code caret])))]
    (with-meta
      (fn []
        [:textarea#text-editor.form-control
         {:on-change (fn [e]
                       (if-not (:editing? @edit)
                         (and (js/confirm "コードを編集するとブロックでの操作ができなくなります。本当に編集しますか？")
                              (update-code e))
                         (update-code e)))
          :on-blur (fn [e]
                     (when (:editiong? @edit)
                       (update-code e)))
          :default-value (:code @edit)}])
      {:component-did-update (fn [this _ _]
                               (set! (.-value (reagent/dom-node this))
                                     (:code @edit)))})))

(def editor
  (let [view (r/subscribe [:view])
        view-display #(array-map :display (if (= @view %1):block :none))]
    (with-meta
      (fn []
        [:div.editor
         [:div#blockly {:style (view-display :block)}]
         [:form#text-editor {:style (view-display :code)}
          [text-editor]]])
      {:component-did-mount (fn [_]
                              (r/dispatch [:after-editor-mount @view]))
       :component-did-update (fn [_ _ _]
                               (r/dispatch [:after-editor-update @view]))})))

(def logging-textarea
  (let [logs (r/subscribe [:logs])]
    (fn []
      [:textarea.logging-textarea.form-control
       {:read-only true, :value @logs}])))

(defn logging-modal []
  (fn []
    [:div#logging-area.modal.fade {:role "dialog" :tabIndex "-1"}
     [:div.modal-dialog
      [:div.modal-content
       [:div.modal-header
        [:h4.modal-title "ビルドログ"]]
       [:div.modal-body
        [logging-textarea]]
       [:div.modal-footer
        [:button.btn.btn-default {:data-dismiss "modal"}
         "閉じる"]]]]]))

(def app
  (let [settings-pane-shown? (r/subscribe [:settings-pane-shown?])]
    (with-meta
      (fn []
        [:div#container.container-fluid
         [header-menu]
         [:div
          [editor]
          [logging-modal]
          [settings/server-log-modal]
          [settings/settings-modal]]])
      {:component-did-mount (fn [_]
                              (r/dispatch [:initialize-app]))})))

