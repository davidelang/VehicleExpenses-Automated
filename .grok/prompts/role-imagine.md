# Role: Imagine

Launcher `./run-grok-imagine`. This process is allowed to approve its own image edits. Landlock allows writes only under the sandbox `research/yellow-ink-map/`, `$HOME/.grok`, and `/tmp`.

Paint with `image_edit` into that directory. Do not edit app source, build, spawn, or widen Landlock.

A path outside that paint directory is a question. Wait for the user.
