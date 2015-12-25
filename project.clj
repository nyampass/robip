(defproject robip "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374"]
                 [reagent "0.5.0"]
                 [re-frame "0.6.0"]
                 [re-com "0.8.0"]
                 [cljs-ajax "0.5.2"]]

  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0-1"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]

                :compiler {:main robip.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/robip.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true
                           :verbose true
                           :libs ["resources/public/js/core/blocks.js"
                                  "resources/public/js/core/block.js"
                                  "resources/public/js/core/contextmenu.js"
                                  "resources/public/js/core/field_image.js"
                                  "resources/public/js/core/inject.js"
                                  "resources/public/js/core/scrollbar.js"
                                  "resources/public/js/core/workspace.js"
                                  "resources/public/js/core/css.js"
                                  "resources/public/js/core/field_label.js"
                                  "resources/public/js/core/input.js"
                                  "resources/public/js/core/toolbox.js"
                                  "resources/public/js/core/workspace_svg.js"
                                  "resources/public/js/core/block_svg.js"
                                  "resources/public/js/core/field.js"
                                  "resources/public/js/core/field_textinput.js"
                                  "resources/public/js/core/msg.js"
                                  "resources/public/js/core/tooltip.js"
                                  "resources/public/js/core/xml.js"
                                  "resources/public/js/core/blockly.js"
                                  "resources/public/js/core/field_angle.js"
                                  "resources/public/js/core/field_variable.js"
                                  "resources/public/js/core/mutator.js"
                                  "resources/public/js/core/trashcan.js"
                                  "resources/public/js/core/field_checkbox.js"
                                  "resources/public/js/core/flyout.js"
                                  "resources/public/js/core/names.js"
                                  "resources/public/js/core/utils.js"
                                  "resources/public/js/core/bubble.js"
                                  "resources/public/js/core/field_colour.js"
                                  "resources/public/js/core/generator.js"
                                  "resources/public/js/core/procedures.js"
                                  "resources/public/js/core/variables.js"
                                  "resources/public/js/core/comment.js"
                                  "resources/public/js/core/field_date.js"
                                  "resources/public/js/core/realtime-client-utils.js"
                                  "resources/public/js/core/warning.js"
                                  "resources/public/js/core/connection.js"
                                  "resources/public/js/core/field_dropdown.js"
                                  "resources/public/js/core/icon.js"
                                  "resources/public/js/core/realtime.js"
                                  "resources/public/js/core/widgetdiv.js"
                                  "resources/public/js/blocks/blocks-base.js"
                                  "resources/public/js/blocks/blocks-colour.js"
                                  "resources/public/js/blocks/blocks-grove.js"
                                  "resources/public/js/blocks/blocks-lists.js"
                                  "resources/public/js/blocks/blocks-logic.js"
                                  "resources/public/js/blocks/blocks-loops.js"
                                  "resources/public/js/blocks/blocks-math.js"
                                  "resources/public/js/blocks/blocks-procedures.js"
                                  "resources/public/js/blocks/blocks-text.js"
                                  "resources/public/js/blocks/blocks-variables.js"
                                  "resources/public/js/generators/arduino.js"
                                  "resources/public/js/generators/arduino/arduino-base.js"
                                  "resources/public/js/generators/arduino/arduino-control.js"
                                  "resources/public/js/generators/arduino/arduino-grove.js"
                                  "resources/public/js/generators/arduino/arduino-logic.js"
                                  "resources/public/js/generators/arduino/arduino-math.js"
                                  "resources/public/js/generators/arduino/arduino-procedures.js"
                                  "resources/public/js/generators/arduino/arduino-text.js"
                                  "resources/public/js/generators/arduino/arduino-variables.js"
                                  "resources/public/js/msg/en.js"
                                  ]}}
               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/compiled/robip.js"
                           :main robip.core
                           :optimizations :advanced
                           :pretty-print false}}]}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             })
