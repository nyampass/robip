(ns robip.handlers.electron
  (:require [re-frame.core :as r]
            [cljs.nodejs :as node]))

(def cp (node/require "child_process"))
(def request (node/require "request"))
(def os (node/require "os"))
(def fs (node/require "fs"))
(def path (node/require "path"))
(def remote (node/require "remote"))

(r/register-handler
 :download-binary
 [r/trim-v]
 (fn [db [path]]
   (log "ダウンロード中...")
   (request (str robip-server-uri path)
            #js{:encoding nil}
            (fn [err res body]
              (if-not err
                (r/dispatch [:write-to-file body])
                (error "ビルドに失敗しました"))))
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
                       (error "ビルドに失敗しました")))))
   db))

(r/register-handler
 :upload-to-device
 [r/trim-v]
 (fn [db [file-path]]
   (log "書き込み中...")
   (let [lib-path (path.join "lib" "robip-tool" "robip-tool.jar")
         proc (->> #js["-jar" lib-path "--default-port" "0" file-path]
                   (cp.spawn "java"))
         err (atom "")]
     (.on proc "exit"
          (fn [code signal]
            (if (= code 0)
              (r/dispatch [:upload-complete])
              (error (str "書き込みに失敗しました\n" @err)))))
     (.on (.-stderr proc) "data"
          (fn [data] (swap! err str data))))
   (assoc db :build-progress :uploading)))
