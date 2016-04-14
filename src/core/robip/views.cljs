(ns robip.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [robip.authentication :as auth]
            robip.handlers.core
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
        view-selector #(fn [e] (r/dispatch [:select-view %]))]
    (fn []
      [:ul.nav.navbar-nav
       [:li {:class (-> (cond-class "" (= @view :block) "active")
                        (cond-class (:editing? @edit) "disabled"))
             :role "presentation"}
        (if (not (:editing? @edit))
          (wrap-link (view-selector :block)
                     '([:i.fa.fa-th-large] " ブロック"))
          [:p.navbar-text
           '([:i.fa.fa-th-large] " ブロック")])]
       [:li {:class (cond-class "" (= @view :code) "active")
             :role "presentation"}
        (wrap-link (view-selector :code)
                   [:i.fa.fa-pencil-square-o] " コード")]])))

(defn setting-input-field [field-name label]
  (let [content (r/subscribe [:settings field-name])]
    (fn [field-name placeholder]
      [:div.pure-control-group
       [:label {:for (name field-name)} label]
       [:input.pure-u-1-2.pure-u-md-7-12
        {:name (name field-name) :type "text" :placeholder label
         :default-value @content
         :on-blur (fn [e]
                    (let [new-content (.. e -target -value)]
                      (r/dispatch [:update-setting field-name new-content])))}]])))

(defn setting-wifi-input-field [index setting]
  (let [ssid-field-name (str "wifi-ssid-" index)
        password-field-name (str "wifi-password-" index)]
    (prn :setting-wifi-input-field index setting)
    (fn []
      [:div
       [:div.pure-control-group
        [:label {:for ssid-field-name} (str "Wifi SSID(" (inc index) ")")]
        [:input.pure-button.button-xsmall.button-secondary
         {:type "button" :value "x"
          :on-click (fn [e]
                      (r/dispatch [:remove-wifi-setting index]))}]
        [:input.pure-u-1-2.pure-u-md-7-12
         {:name ssid-field-name :type "text" :placeholder ""
          :default-value (:ssid setting)
          :on-blur (fn [e]
                     (let [new-content (.. e -target -value)]
                       (r/dispatch [:update-wifi-setting :ssid index
                                    new-content])))}]
        ]
       [:div.pure-control-group
        [:label {:for password-field-name} (str "Wifi パスワード(" (inc index) ")")]
        [:input.pure-u-1-2.pure-u-md-7-12
         {:name password-field-name :type "text" :placeholder ""
          :default-value (:password setting)
          :on-blur (fn [e]
                     (let [new-content (.. e -target -value)]
                       (r/dispatch [:update-wifi-setting :password index
                                    new-content])))}]]])))

(defn settings-pane []
  (let [wifi-settings (r/subscribe [:wifi-settings])]
    (fn []
      [:div.pure-u-1.settings-menu
       [:div.pure-g
        [:div#settings-pane.pure-u-1.pure-u-md-5-12
         [:div.pure-g
          [:div.pure-u-1
           [:form.pure-form.pure-form-aligned
            [:fieldset
             [setting-input-field :robip-id "Robip ID"]
             [:div
              (keep-indexed (fn [i setting]
                              ^{:key setting}
                              [setting-wifi-input-field i setting])
                            @wifi-settings)]
             [:input {:type "button" :value "Wifiの追加"
                      :on-click (fn [e]
                                  (r/dispatch [:append-wifi-setting]))}]]]]]]]])))

(defn menu []
  (let [build-progress (r/subscribe [:build-progress])
        workspace (r/subscribe [:workspace])
        robip-id (r/subscribe [:settings :robip-id])
        edit (r/subscribe [:edit])]
    (fn []
      [:ul.nav.navbar-nav.navbar-right.nav-pills
       (list
        [:li
         [auth/signup]]
        [:li
         [auth/login]]
        (let [disabled? (or (not= @build-progress :done) (empty? @robip-id))]
          [:li#build-menu
           [:button
            (let [button-attrs
                  {:type "submit"
                   :class "btn navbar-btn btn-primary"}]
              (if disabled?
                (assoc button-attrs :disabled "disabled")
                (assoc button-attrs (click-handler-attr)
                       (fn [e] (r/dispatch [:build])))))
            '([:i.fa.fa-arrow-circle-right] [:b " ビルド"])]])
        [:li
         (wrap-link (fn [e] (r/dispatch [:toggle-settings-pane]))
                    [:i.fa.fa-ellipsis-v])])])))

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
                       (when-not (:editing? @edit)
                         (and (js/confirm "コードを編集するとブロックでの操作ができなくなります。本当に編集しますか？")
                              (update-code e))))
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
         (when @settings-pane-shown?
           [settings-pane])
         [:div
          [editor]
          [logging-modal]]])
      {:component-did-mount (fn [_]
                              (r/dispatch [:initialize-app]))})))


