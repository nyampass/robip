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
    (fn [field-name placeholder]
      [:div.form-group
       [:label.col-sm-4.control-label {:for (name field-name)} label]
       [:div.col-sm-8
        [:input.form-control
         {:name (name field-name) :type "text" :placeholder label
          :default-value @content
          :on-blur (fn [e]
                     (let [new-content (.. e -target -value)]
                       (r/dispatch [:update-setting field-name new-content])))}]]])))

(defn setting-wifi-input-field [{:keys [setting index]}]
  (let [wifi-settings (r/subscribe [:wifi-settings])
        ssid-field-name (str "wifi-ssid-" index)
        password-field-name (str "wifi-password-" index)]
    (fn []
      [:div
       [:div.form-group {:key (-> setting meta :key)}
        [:div.col-sm-4.control-label
         [:label {:for ssid-field-name} (str "Wifi SSID(" (inc index) ")")]
         ]
        [:div.col-sm-8
         [:input.form-control
          {:name ssid-field-name :type "text" :placeholder ""
           :default-value (:ssid setting)
           :on-blur (fn [e]
                      (let [new-content (.. e -target -value)]
                        (r/dispatch [:update-wifi-setting :ssid index
                                      new-content])))}]]]
       [:div.form-group
        [:div.col-sm-4.control-label
         [:label {:for password-field-name} (str "パスワード(" (inc index) ")")]]
        [:div.col-sm-8
         [:input.form-control
          {:name password-field-name :type "text" :placeholder ""
           :default-value (:password setting)
           :on-blur (fn [e]
                      (let [new-content (.. e -target -value)]
                        (r/dispatch [:update-wifi-setting :password index
                                     new-content])))}]
         (when (= (inc index) (count @wifi-settings))
           [:input.btn
            {:type "button" :value "x"
             :on-click (fn [e]
                         (r/dispatch [:remove-wifi-setting index]))}])]]])))

(defn settings-pane []
  (let [wifi-settings (r/subscribe [:wifi-settings])]
    (fn []
      [:div.settings-menu
       [:div#settings-pane
        [:form.form-horizontal
         [setting-input-field :robip-id "Robip ID"]
         (keep-indexed (fn [i setting]
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
