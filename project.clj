(defproject com.2tothe8th/expect "0.1.0"
  :description "Unit-testing library."
  :url "https://github.com/dubiousdavid/expect"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.roomkey/annotate "1.0.0"]
                 [org.clojure/core.match "0.2.2"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [stch-library/glob "0.2.0"]
                 [com.2tothe8th/monads "0.1.0"]]
  :profiles {:dev {:jvm-opts ["-Dannotate.typecheck=on"]
                   :dependencies [[midje "1.6.3"]]
                   :codox {:src-dir-uri "https://github.com/dubiousdavid/expect/blob/master/"
                           :src-linenum-anchor-prefix "L"
                           :output-dir "."}}})
