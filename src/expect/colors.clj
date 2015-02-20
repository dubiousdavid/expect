(ns expect.colors)

(defn colorize [ansi s]
  (str "\u001B[" ansi "m" s "\u001B[0m"))

(def bold (partial colorize 1))
(def red (partial colorize 31))
(def green (partial colorize 32))
(def yellow (partial colorize 33))
(def blue (partial colorize 34))
(def magenta (partial colorize 35))
(def cyan (partial colorize 36))
