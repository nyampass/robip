(ns robip.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            robip.handlers.core
            robip.subs
            #_[cljs.nodejs :as node]))

#_(def path (node/require "path"))

(defn header []
  (let [build-progress (r/subscribe [:build-progress])
        workspace (r/subscribe [:workspace])
        edit (r/subscribe [:edit])
        button (fn [attrs body]
                 [:button (merge {:type "button"
                                  :class "build-button pure-button"
                                  :title "ビルド"}
                                 attrs)
                  body])]
    (fn []
      (let [content [:i {:class "fa fa-arrow-circle-right"}]
            file-path (:file-path @edit)]
       [:div.pure-u-1
        [:div.header
         (if (= @build-progress :done)
           (button {:on-click (fn [e] (r/dispatch [:build]))} content)
           (button {:disabled true} content))
         #_[:div.header-title (or (some-> file-path path.basename)
                                  "タイトルなし")]]]))))

(defn view-selector []
  (let [view (r/subscribe [:view])
        edit (r/subscribe [:edit])
        cond-pure-class #(if %2 (str %1 " pure-menu-" %3) %1)
        view-selector #(fn [e] (r/dispatch [:select-view %]))
        wrap-link (fn [view text]
                    [:a.pure-menu-link {:on-click (view-selector view)}
                     text])]
    (fn []
      [:div.view-selector.pure-u-1
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
        [:textarea.text-editor.pure-input-1
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
        [:div.editor.pure-u-1
         [:div#blockly.pure-u-1 {:style (view-display :block)}]
         [:form#text-editor.pure-form {:style (view-display :code)}
          [text-editor]]])
      {:component-did-mount (fn [_]
                              (r/dispatch [:after-editor-mount @view]))
       :component-did-update (fn [_ _ _]
                               (r/dispatch [:after-editor-update @view]))})))

(def logging-textarea
  (let [logs (r/subscribe [:logs])]
    (with-meta
      (fn []
        [:textarea.logging-textarea.pure-input-1
         {:read-only true, :value @logs}])
      {:component-did-update
       (fn [this _ _]
         (r/dispatch [:after-logging (reagent/dom-node this)]))})))

(defn logging-area []
  (fn []
    [:div.logging-area.pure-u-1
     [:form.pure-form
      [logging-textarea]]]))

(defn app []
  [:div.pure-g
   [header]
   [view-selector]
   [editor]
   [logging-area]])
