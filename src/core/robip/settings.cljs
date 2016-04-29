(ns robip.settings
  (:require [re-frame.core :as r]))

(defn ssid-key->index [key]
  (if-let [matched (re-seq #"ssid-(\d+)" (name key))]
    (-> matched first second js/parseInt)))

(defn index->ssid-key [index]
  (keyword (str "ssid-" index)))

(defn index->password-key [index]
  (keyword (str "password-" index)))

(defn wifi-settings [{{wifi :wifi} :settings :as db}]
  (prn :wifi-settings wifi)
  wifi)

(defn setting-input-field [field-name label]
  (let [content (r/subscribe [:settings field-name])]
    (fn []
      (prn :setting-input-field field-name @content)
      [:div.form-group
       [:label.col-sm-3.control-label {:for (name field-name)} label]
       [:div.col-sm-9
        [:input.form-control
         {:name (name field-name) :type "text" :placeholder ""
          :default-value @content
          :on-blur (fn [e]
                     (let [new-content (.. e -target -value)]
                       (r/dispatch [:update-setting field-name new-content])))}]]])))

(defn setting-wifi-input-field [{:keys [setting index]}]
  (let [wifi-settings (r/subscribe [:wifi-settings])
        ssid-field-name (str "wifi-ssid-" index)
        password-field-name (str "wifi-password-" index)]
    (fn []
      [:div.div.form-group
       [:div.col-sm-3.control-label
        [:label {:for ssid-field-name} (str "Wifi(" (inc index) ")")]]
       [:div.col-sm-9.form-inline
        [:div.form-group
         [:input.form-control
          {:name ssid-field-name :type "text" :placeholder "SSID"
           :default-value (:ssid setting)
           :on-blur (fn [e]
                      (let [new-content (.. e -target -value)]
                        (r/dispatch [:update-wifi-setting :ssid index
                                     new-content])))}]]
        [:div.form-group
         [:input.form-control
          {:name password-field-name :type "text" :placeholder "パスワード"
           :default-value (:password setting)
           :on-blur (fn [e]
                      (let [new-content (.. e -target -value)]
                        (r/dispatch [:update-wifi-setting :password index
                                     new-content])))}]]
        [:div.form-group
         [:input.btn
          {:type "button" :value "x"
           :on-click (fn [e]
                       (r/dispatch [:remove-wifi-setting index]))}]]]])))

(defn settings-pane []
  (let [wifi-settings (r/subscribe [:wifi-settings])]
    (fn []
      [:div.settings-menu
       [:div#settings-pane
        [:form
         ^{:key (gensym)} [setting-input-field :robip-id "Robip ID"]
         (keep-indexed (fn [i setting]
                         ^{:key (str (-> setting :key) "-" i)}
                         [setting-wifi-input-field {:index i, :setting setting}])
                       @wifi-settings)
         [:input.btn.btn-default {:type "button" :value "Wifiの追加"
                                  :on-click (fn [e]
                                              (r/dispatch [:append-wifi-setting]))}]]]])))

(defn settings-modal []
  (fn []
    [:div#settings-modal.modal.fade {:role "dialog" :tabIndex "-1"}
     [:div.modal-dialog
      [:div.modal-content
       [:div.modal-header
        [:h4.modal-title "設定"]]
       [:div.modal-body
        [settings-pane]]
       [:div.modal-footer
        [:button.btn.btn-default {:data-dismiss "modal"}
         "閉じる"]]]]]))

(defn server-log-pane []
  (let [logs (r/subscribe [:server-logs])]
    (fn []
      [:div (map (fn [log] (list log [:br])) @logs)])))

(defn server-log-modal []
  (fn []
    [:div#server-log-modal.modal.fade {:role "dialog" :tabIndex "-1"}
     [:div.modal-dialog
      [:div.modal-content
       [:div.modal-header
        [:h4.modal-title "ログ"]]
       [:div.modal-body
        [server-log-pane]]
       [:div.modal-footer
        [:button.btn.btn-default {:data-dismiss "modal"}
         "閉じる"]]]]]))
