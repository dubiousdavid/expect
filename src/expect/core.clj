(ns expect.core
  (:require [annotate
             [core :refer [check]]
             [fns :refer [defn$]]
             [types :refer [U KwA Vec Int Option Seq Symbol Fn Seqable]]]
            [clojure.core.match :refer [match]]
            [expect.types :refer [NsOrSym Level TestableCountable Errors NsErrors]]
            [monads.state :refer [state-> return-state statefn eval-state]]
            [expect.util :refer [sum init-ns-es reconstruct-args
                                 repeat-str space ns->sym ns-matches]]
            [expect.colors :as color]
            [expect.protocols :as proto :refer [teste counte takev]]
            [clojure.core.async :as async :refer [go <! <!! alts!!]]
            clojure.core.async.impl.protocols)
  (:import [clojure.lang APersistentMap Sequential IDeref]
           [clojure.core.async.impl.protocols ReadPort]))

(def ^{:dynamic true :private true} *top-level* true)
(def ^{:dynamic true :private true} *padding* 0)
(def ^{:private false} expectations (atom {}))

(defn$ loaded-namespaces [=> (Seq Symbol)]
  "Returns a sequence of loaded namespace symbols."
  []
  (keys @expectations))

(extend-protocol proto/Countable
  nil
  (counte [this] 0)
  APersistentMap
  (counte [this] (sum (map counte (vals this))))
  Sequential
  (counte [this] (sum (map counte this))))

(defn$ adde [TestableCountable => (Option TestableCountable)]
  "Add expectation."
  [e]
  (if *top-level*
    (let [curr-ns (ns-name *ns*)]
      (swap! expectations update-in [curr-ns] init-ns-es e)
      nil)
    e))

(defrecord Expectation [code expected f]
  proto/Testable
  (teste [this]
    (statefn [_]
      (let [val (f)]
        (match val
          [::timeout timeout] [code (space "Timed out after" timeout "ms")]
          [::exception e] [code (space "Caught:" (.toString e))]
          [::did-not-throw v] [code (space "Expected a thrown exception. Actual:" v)]
          :else (when-let [error (check expected val)]
                  [code error])))))

  proto/Countable
  (counte [this] 1))

(extend-protocol proto/Async
  IDeref
  (takev [this timeout]
    (if (> timeout 0)
      (deref this timeout [::timeout timeout])
      (deref this)))

  ReadPort
  (takev [this timeout]
    (if (> timeout 0)
      (let [ch (go (<! (async/timeout timeout)) [::timeout timeout])]
        (first (alts!! [this ch])))
      (<!! this))))

(defmacro handle-timeout [form timeout]
  `(if (> ~timeout 0)
     (let [ch# (go (<! (async/timeout ~timeout)) [::timeout ~timeout])]
       (first (alts!! [(go ~form) ch#])))
     ~form))

(defmacro expect [expected actual & {:keys [async timeout throws]
                                     :or {async false, timeout 0, throws false}}]
  "Generate an expectation passing the expected value and an expression. Optionally
  specify a timeout in ms, whether you expect an exception to be thrown, and
  whether the expression returns an asynchronous value. Supported asynchronous
  values are anything that implements IDeref and core.async channels."
  (let [actual' (if async
                  `(takev ~actual ~timeout)
                  `(handle-timeout ~actual ~timeout))
        f (if throws
            `(fn []
               (try
                 (let [v# ~actual']
                   [::did-not-throw v#])
                 (catch Exception e# e#)))
            `(fn []
               (try
                 ~actual'
                 (catch Exception e# [::exception e#]))))
        args (reconstruct-args async throws timeout)
        code (list* 'expect expected actual args)]
    `(adde (Expectation. '~code ~expected ~f))))

(defn$ test-es [(Seqable TestableCountable) => Fn]
  "Test expectations."
  {:private true}
  [es]
  (statefn [map-fn]
    (->> es
         (map teste)
         (map-fn #(eval-state % map-fn))
         (remove nil?)
         vec)))

(defrecord DescribeBlock [description body]
  proto/Testable
  (teste [this]
    (state-> [errors (test-es body)]
      (return-state
       (when (seq errors)
         [description errors]))))

  proto/Countable
  (counte [this] (sum (map counte body))))

(defmacro describe [description & body]
  "Describe a group of expectations."
  `(adde (DescribeBlock. ~description
                        (binding [*top-level* false]
                          (flatten (list ~@body))))))

(defn$ test-ns [NsOrSym => Errors]
  "Test expectations for the given namespace."
  [namespace]
  (let [es (@expectations (ns->sym namespace))]
    (eval-state (test-es es) map)))

(defn$ ptest-ns [NsOrSym => Errors]
  "Test expectations for the given namespace. Will spawn
  a new thread for each expectation."
  [namespace]
  (let [es (@expectations (ns->sym namespace))]
    (eval-state (test-es es) pmap)))

(defn$ test-all* [(Seq Symbol) => NsErrors]
  {:private true}
  [ns-syms]
  (mapv (fn [ns-sym] [ns-sym (test-ns ns-sym)]) ns-syms))

(defn$ test-all [=> NsErrors]
  "Test expectations for all namespaces."
  []
  (let [ns-syms (loaded-namespaces)]
    (test-all* ns-syms)))

(defn$ ptest-all* [(Seq Symbol) Level => NsErrors]
  {:private true}
  [ns-syms level]
  (case level
    :ex (mapv (fn [ns-sym] [ns-sym (ptest-ns ns-sym)]) ns-syms)
    :ns (vec (pmap (fn [ns-sym] [ns-sym (test-ns ns-sym)]) ns-syms))))

(defn$ ptest-all [& (KwA :level Level) => NsErrors]
  "Test expectations for all namespaces. Will spawn a new thread
  for each expectation or namespace. Default is a new thread for
  each namespace."
  [& {:keys [level] :or {level :ns}}]
  (let [ns-syms (loaded-namespaces)]
    (ptest-all* ns-syms level)))

(defn$ clear-ns [NsOrSym =>]
  "Clear expectations for the given namespace."
  [namespace]
  (swap! expectations dissoc (ns->sym namespace))
  nil)

(defn clear-all
  "Clear expectations for all namespaces."
  []
  (reset! expectations {})
  nil)

(defn$ retest-ns [NsOrSym => Errors]
  "Reload expectations for the given namespace and test."
  [namespace]
  (let [ns-sym (ns->sym namespace)]
    (clear-ns ns-sym)
    (require :reload ns-sym)
    (test-ns ns-sym)))

(defmacro pad-left
  "Increase left padding by two spaces."
  [& body]
  `(binding [*padding* (+ *padding* 2)]
     ~@body))

(defn ^String pad-data
  "Left pad form by *padding* spaces."
  [form]
  (str (repeat-str *padding* " ") form))

(def ^{:doc "Pad data and print."} pr-data
  (comp println pad-data))

(defn$ print-errors [Errors =>]
  "Print errors with appropriate padding and colors."
  {:private true}
  [errors]
  (doseq [error errors]
    (match error
      [(description :guard string?) block-errors]
      (do (pr-data (color/cyan description))
          (pad-left (print-errors block-errors)))

      [code actual]
      (do (pr-data code)
          (pr-data (color/magenta actual))))))

(defn$ format-error-totals [Int Int => String]
  {:private true}
  [num-errors num-es]
  (let [result (space "Ran" num-es "test(s).")]
    (if (> num-es 0)
      (if (> num-errors 0)
        (color/magenta (space result num-errors "error(s) found."))
        (color/green (space result "No errors found.")))
      (color/green result))))

(defn$ count-errors [Errors => Int]
  {:private true}
  [errors]
  (sum (for [error errors]
         (match error
           [(_ :guard string?) block-errors]
           (count-errors block-errors)
           [_ _] 1))))

(defn$ run-ns* [Symbol Fn =>]
  {:private true}
  [ns-sym test-fn]
  (let [num-es (counte (@expectations ns-sym))
        errors (test-fn ns-sym)]
    (when (seq errors)
      (prn)
      (print-errors errors)
      (prn))
    (println (format-error-totals (count-errors errors) num-es))))

(defn$ run-ns [NsOrSym =>]
  "Test expectations for the given namespace and print the results."
  [namespace]
  (run-ns* (ns->sym namespace) test-ns))

(defn$ prun-ns [NsOrSym =>]
  "Test expectations for the given namespace and print the results.
  Spawn a new thread for each expectation."
  [namespace]
  (run-ns* (ns->sym namespace) ptest-ns))

(defn$ print-ns-errors [NsErrors =>]
  {:private true}
  [ns-errors]
  (doseq [[ns-sym errors] ns-errors]
    (when (seq errors)
      (prn)
      (pr-data (color/cyan ns-sym))
      (pad-left (print-errors errors))
      (prn))))

(defn$ count-ns-errors [NsErrors => Int]
  {:private true}
  [ns-errors]
  (->> ns-errors (mapcat second) count-errors))

(defn$ run-ns-pattern [String =>]
  [pattern]
  (let [matches (ns-matches (loaded-namespaces) pattern)]
    (when (seq matches)
      (let [num-es (counte (select-keys @expectations matches))
            ns-errors (test-all* matches)
            num-errors (count-ns-errors ns-errors)]
        (print-ns-errors ns-errors)
        (println (format-error-totals num-errors num-es))))))

(defmacro run-ns# [pattern]
  `(run-ns-pattern ~(name pattern)))

(defn$ prun-ns-pattern [String Level =>]
  [pattern level]
  (let [matches (ns-matches (loaded-namespaces) pattern)]
    (when (seq matches)
      (let [num-es (counte (select-keys @expectations matches))
            ns-errors (ptest-all* matches level)
            num-errors (count-ns-errors ns-errors)]
        (print-ns-errors ns-errors)
        (println (format-error-totals num-errors num-es))))))

(defmacro prun-ns# [pattern & {:keys [level] :or {level :ns}}]
  `(prun-ns-pattern ~(name pattern) ~level))

(defn$ rerun-ns [NsOrSym =>]
  "Reload expectations for the given namespace, test and print the results."
  [namespace]
  (let [ns-sym (ns->sym namespace)]
    (clear-ns ns-sym)
    (require :reload ns-sym)
    (run-ns ns-sym)))

(defn$ run-all* [NsErrors =>]
  {:private true}
  [ns-errors]
  (let [num-es (counte @expectations)
        num-errors (count-ns-errors ns-errors)]
    (print-ns-errors ns-errors)
    (println (format-error-totals num-errors num-es))))

(defn run-all
  "Test expectations for all namespaces and print the results."
  []
  (run-all* (test-all)))

(defn$ prun-all [& (KwA :level Level) =>]
  "Test expectations for all namespaces and print the results.
  Will spawn a  new thread for each expectation or namespace.
  Default is a new thread for each namespace."
  [& {:keys [level] :or {level :ns}}]
  (run-all* (ptest-all :level level)))
