(defproject expect "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.roomkey/annotate "0.12.5-4"]
                 [org.clojure/core.match "0.2.2"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [stch-library/glob "0.2.0"]
                 [com.roomkey/monads "0.9.1"]]
  :profiles {:dev {:jvm-opts ["-Drk.annotate.typecheck=on"]
                   :dependencies [[midje "1.6.3"]]}})
