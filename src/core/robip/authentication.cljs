(ns robip.authentication
  (:require [re-com.core :refer [scroller h-box v-box box gap line border title label
                                 modal-panel progress-bar input-text checkbox button p]]
            [robip.utils :refer [panel-title title2 args-table github-hyperlink status-text]]
            [re-frame.core :as r]
            [re-com.modal-panel :refer [modal-panel-args-desc]]
            [reagent.core :as reagent]))

(defn signup-dialog-markup
  [form-data process-ok process-cancel]
  [:div#signup-dialog.modal.fade.in {:tabindex "-1" :role "dialog" :style {:display "block"}}
   [:div.modal-dialog
    [:div.modal-content
     [:div.modal-header
      [:h4.modal-title
       "Robipへの参加しよう！"]]
     [:div.modal-body
      [v-box
       :children 
       [[label :label [:p "登録いただくことで、PC、スマホ間でプログラムを引き継ぐことができます"
                       [:br] "メールアドレスには保護者の方のアドレスを入れてください"]]
        [:a.btn.btn-large.btn-facebook {:href (str "/login/facebook/"
                                                   (if (re-seq #"app.html" (.-pathname (.-location js/window)))
                                                     "app"
                                                     "default"))}
         [:i.fa.fa-facebook-official] " Facebookでサインアップ"]
        [v-box
         :class    "form-group"
         :children [[:label {:for "pf-email"} "メールアドレス"]
                    [input-text
                     :model       (:email @form-data)
                     :on-change   #(swap! form-data assoc :email %)
                     :placeholder "メールアドレス"
                     :class       "form-control"
                     :attr        {:id "pf-email"}]]]
        [v-box
         :class    "form-group"
         :children [[:label {:for "pf-name"} "ユーザ名"]
                    [input-text
                     :model       (:name @form-data)
                     :on-change   #(swap! form-data assoc :name %)
                     :placeholder "Robip上で使う名前"
                     :class       "form-control"
                     :attr        {:id "pf-name"}]]]
        [v-box
         :class    "form-group"
         :children [[:label {:for "pf-password"} "パスワード"]
                    [input-text
                     :model       (:password @form-data)
                     :on-change   #(swap! form-data assoc :password %)
                     :placeholder "パスワード(4文字以上)"
                     :class       "form-control"
                     :attr        {:id "pf-password" :type "password"}]]]
        [v-box
         :class    "form-group"
         :children [[:label {:for "pf-re-password"} "確認用パスワード"]
                    [input-text
                     :model       (:re-password @form-data)
                     :on-change   #(swap! form-data assoc :re-password %)
                     :placeholder "確認用パスワード"
                     :class       "form-control"
                     :attr        {:id "pf-re-password" :type "password"}]]]]]]
     [:div.modal-footer
      [button
       :label    "登録する"
       :class    "btn btn-primary"
       :on-click process-ok]
      [button
       :label    "キャンセル"
       :on-click process-cancel]]]]])

(defn signup []
  (let [authentication-mode (r/subscribe [:authentication-mode])
        login (r/subscribe [:login])
        form-data (reagent/atom {:email ""
                                 :name ""
                                 :password ""
                                 :re-password ""})
        save-form-data (reagent/atom @form-data)
        process-ok     (fn [event]
                         (r/dispatch [:signup @form-data show?])
                         false)
        process-cancel (fn [event]
                         (reset! form-data @save-form-data)
                         (r/dispatch [:change-authentication-mode :login])
                         false)]
    (fn []
      [v-box
       :children [(if (:id @login)
                    [:a.navbar-text
                     {:style {:display "block"}}
                     (str "" (:name @login) "さん")])
                  (when (= @authentication-mode :signup)
                    [signup-dialog-markup
                     form-data
                     process-ok
                     process-cancel])]])))

(defn login-dialog-markup
  [form-data process-ok]
  [:div#login-dialog.modal.fade.in {:tabindex "-1" :role "dialog" :style {:display "block"}}
   [:div.modal-dialog
    [:div.modal-content
     [:div.modal-header
      [:h4.modal-title
       "Robipへログイン！"]]
     [:div.modal-body
      [v-box
       :children 
       [[:a.btn.btn-large.btn-facebook {:href (str "/login/facebook/"
                                                   (if (re-seq #"app.html" (.-pathname (.-location js/window)))
                                                     "app"
                                                     "default"))}
         [:i.fa.fa-facebook-official] " Facebookログイン"]        
        [:button.btn.btn-link {:style {"color" "#23527c"}
                               :on-click (fn [e]
                                           (r/dispatch [:change-authentication-mode :signup]))}
         "アカウントをお持ちでない方はこちらから作成してください"]
        [:div.form-group
         [:div.col-xs-3.control-label
          [:label {:for "email"} "ID"]]
         [:div.col-xs-9
          [input-text
           :model       (:email @form-data)
           :on-change   #(swap! form-data assoc :email %)
           :placeholder ""
           :class       "form-control"
           :attr        {:id "email"}]]]
        [:div.form-group
         [:div.col-xs-3.control-label
          [:label {:for "password"} "パスワード"]]
         [:div.col-xs-9
          [input-text
           :model       (:password @form-data)
           :on-change   #(swap! form-data assoc :password %)
           :placeholder ""
           :class       "control"
           :attr        {:id "pf-password" :type "password"}]]]]]]
     [:div.modal-footer
      [button
       :label    "ログインする"
       :class    "btn btn-primary"
       :on-click process-ok]]]]])

(defn login []
  (let [login (r/subscribe [:login])
        authentication-mode (r/subscribe [:authentication-mode])
        form-data (reagent/atom {:email ""
                                 :password ""})
        save-form-data (reagent/atom nil)
        process-ok     (fn [event]
                         (r/dispatch [:login (:email @form-data) (:password @form-data)])
                         false)
        process-cancel (fn [event]
                         (reset! form-data @save-form-data)
                         false)]
    (fn []
      [v-box
       :children [(if (:id @login)
                    [button
                     :label "ログアウト"
                     :class "btn navbar-btn btn-default btn-link"
                     :on-click #(r/dispatch [:logout])])
                  (when (= @authentication-mode :login)
                    [login-dialog-markup
                     form-data
                     process-ok])]])))
