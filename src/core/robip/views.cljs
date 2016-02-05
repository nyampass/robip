(ns robip.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            robip.handlers.core
            robip.subs))

(defn cond-pure-class [classes cond option]
  (if cond
    (str classes " pure-menu-" option)
    classes))

(defn wrap-link [callback & content]
  `[:a.pure-menu-link {:on-click ~callback} ~@content])

(defn view-selector []
  (let [view (r/subscribe [:view])
        edit (r/subscribe [:edit])
        view-selector #(fn [e] (r/dispatch [:select-view %]))]
    (fn []
      [:div.pure-menu.pure-menu-horizontal
       [:ul.pure-menu-list
        [:li {:class (-> "pure-menu-item"
                         (cond-pure-class (= @view :block) "selected")
                         (cond-pure-class (:editing? @edit) "disabled"))}
         (cond->> '([:i.fa.fa-th-large] " ブロック")
           (not (:editing? @edit)) (wrap-link (view-selector :block)))]
        [:li {:class (-> "pure-menu-item"
                         (cond-pure-class (= @view :code) "selected"))}
         (wrap-link (view-selector :code)
                    [:i.fa.fa-pencil-square-o] " コード")]]])))

(defn menu []
  (let [build-progress (r/subscribe [:build-progress])
        workspace (r/subscribe [:workspace])
        robip-id (r/subscribe [:robip-id])
        edit (r/subscribe [:edit])]
    (fn []
      [:div.pure-menu.pure-menu-horizontal.right-menu
       [:ul.pure-menu-list
        (let [disabled? (or (not= @build-progress :done) (empty? @robip-id))]
          [:li#build-menu {:class (-> "pure-menu-item"
                                      (cond-pure-class disabled? "disabled"))}
           (cond->> '([:i.fa.fa-arrow-circle-right] [:b " ビルド"])
             (not disabled?) (wrap-link (fn [e] (r/dispatch [:build]))))])
        [:li.pure-menu-item
         [:a.pure-menu-link
          [:i.fa.fa-ellipsis-v]]]
        #_[:li.pure-menu-item
           [:input.pure-u-1-8
            {:type "text"
             :on-change #(r/dispatch [:update-robip-id (.. % -target -value)])}
            @robip-id]]]])))

(defn header-menu []
  (fn []
    [:div#header-menu.pure-u-1
     [:div.pure-g
      [:div.pure-u-1-2
       [view-selector]]
      [:div.pure-u-1-2
       [menu]]]]))

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
   [header-menu]
   [editor]
   [logging-area]])
