# robip

FIXME: Write a one-line description of your library/project.

## Overview

FIXME: Write a paragraph about the library/project and highlight its goals.

## Setup

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).
This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    lein clean

To create a production build run:

    lein cljsbuild once min

And open your browser in `resources/public/index.html`. You will not
get live reloading, nor a REPL. 

## Credit

Robip uses Blockly and BlocklyDuino for visual programming, which are both distributed under Apache License 2.0.

- [Blockly](https://github.com/google/blockly)
- [BlocklyDuino](https://github.com/BlocklyDuino/BlocklyDuino)

## License

Copyright © 2015 Nympass Co. Ltd.

Distributed under the Eclipse Public License version 1.0.
