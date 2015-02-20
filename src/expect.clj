(ns expect
  (:use [rk.annotate :only [validate]]
        [clojure.core.match :only [match]]
        [stch.glob :only [match-glob]])
  (:require rk.annotate.types
            [expect.colors :as color]
            [clojure.string :as string]
            [clojure.core.async :as async :refer [go <! <!! alts!!]]
            clojure.core.async.impl.channels)
  (:import [clojure.lang APersistentMap Sequential IDeref]))

(def ^{:dynamic true :private true} *top-level* true)
(def ^{:dynamic true :private true} *padding* 0)
(def ^{:private false} expectations (atom {}))

(defprotocol Testable
  (teste [this] "Test expectation."))

(defprotocol Countable
  (counte [this]))

(defn- sum [xs]
  (reduce + 0 xs))

(extend-protocol Countable
  APersistentMap
  (counte [this] (sum (map counte (vals this))))
  Sequential
  (counte [this] (sum (map counte this))))

(defn- init-ns-es [es e]
  (if es (conj es e) [e]))

(defn adde
  "Add expectation."
  [e]
  (when *top-level*
    (let [curr-ns (ns-name *ns*)]
      (swap! expectations update-in [curr-ns] init-ns-es e)))
  e)

(defrecord Expectation [code expected f]
  Testable
  (teste [this]
    (let [val (f)]
      (match val
        [::timeout timeout] [code (str "Timed out after " timeout " ms")]
        [::exception e] [code (str "Caught: " (.toString e))]
        [::did-not-throw v] [code (str "Expected a thrown exception. Actual: " v)]
        :else (when-let [error (validate expected val)]
                [code error]))))

  Countable
  (counte [this] 1))

(defn- reconstruct-args [async throws timeout]
  (->> [(when async [:async true])
        (when throws [:throws true])
        (when (> timeout 0) [:timeout timeout])]
       (remove nil?)
       flatten))

(defprotocol Async
  (takev [this timeout]
    "Take a value from an async source, while handling timeouts."))

(extend-protocol Async
  IDeref
  (takev [this timeout]
    (if (> timeout 0)
      (deref this timeout [::timeout timeout])
      (deref this)))

  clojure.core.async.impl.channels.ManyToManyChannel
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

(defn- test-es [es]
  (->> es (map teste) (remove nil?) vec))

(defrecord DescribeBlock [description body]
  Testable
  (teste [this]
    (let [errors (test-es body)]
      (when (seq errors)
        [description errors])))

  Countable
  (counte [this] (sum (map counte body))))

(defmacro describe [description & body]
  `(adde (DescribeBlock. ~description
                        (binding [*top-level* false]
                          (flatten (list ~@body))))))

(defn test-ns [ns-sym]
  (let [es (@expectations ns-sym)]
    (test-es es)))

(defn test-all []
  (let [ns-syms (keys @expectations)]
    (mapv (fn [ns-sym] [ns-sym (test-ns ns-sym)]) ns-syms)))

(defn clear-ns [ns-sym]
  (swap! expectations dissoc ns-sym))

(defn clear-all []
  (reset! expectations {}))

(defn retest-ns [ns-sym]
  (clear-ns ns-sym)
  (require :reload ns-sym)
  (test-ns ns-sym))

(defmacro pad-left
  [& body]
  `(binding [*padding* (+ *padding* 2)]
     ~@body))

(defn- repeat-str [n ^String s]
  (apply str (repeat n s)))

(defn- pad-data [form]
  (str (repeat-str *padding* " ") form))

(def ^{:private true} pr-data
  (comp println pad-data))

(defn- print-errors [errors]
  (doseq [error errors]
    (match error
      [(description :guard string?) block-errors]
      (do (pr-data (color/cyan description))
          (pad-left (print-errors block-errors)))

      [code actual]
      (do (pr-data code)
          (pr-data (color/magenta actual))))))

(defn- format-error-totals [num-errors num-es]
  (let [num-ran (str "Ran " num-es " test(s).")]
    (if (> num-errors 0)
      (color/magenta (str "Result: " num-ran " " num-errors " error(s) found."))
      (color/green (str "Result: " num-ran " No errors found.")))))

(defn- count-errors [errors]
  (sum (for [error errors]
         (match error
           [(_ :guard string?) block-errors]
           (count-errors block-errors)
           [_ _] 1))))

(defn run-ns [ns-sym]
  (let [num-es (counte (@expectations ns-sym))
        errors (test-ns ns-sym)]
    (when (seq errors)
      (prn)
      (print-errors errors)
      (prn))
    (println (format-error-totals (count-errors errors) num-es))))

(defn- ns-matches [pattern]
  (for [ns-sym (keys @expectations)
        :when (match-glob pattern (str ns-sym))]
    ns-sym))

(defn run-ns* [pattern]
  (let [matches (ns-matches pattern)]
    (when (seq matches)
      (let [num-errors (atom 0)
            num-es (counte (select-keys @expectations matches))]
        (doseq [ns-sym matches
                errors (test-ns ns-sym)]
          (when (seq errors)
            (swap! num-errors + (count-errors errors))
            (prn)
            (pr-data (color/cyan ns-sym))
            (pad-left (print-errors errors))
            (prn)))
        (println (format-error-totals @num-errors num-es))))))

(defmacro run-ns# [pattern]
  `(run-ns* ~(name pattern)))

(defn rerun-ns [ns-sym]
  (clear-ns ns-sym)
  (require :reload ns-sym)
  (run-ns ns-sym))

(defn run-all []
  (let [num-es (counte @expectations)
        num-errors (atom 0)]
    (doseq [[ns-sym errors] (test-all)]
      (when (seq errors)
        (swap! num-errors + (count-errors errors))
        (prn)
        (pr-data (color/cyan ns-sym))
        (pad-left (print-errors errors))
        (prn)))
    (println (format-error-totals @num-errors num-es))))

(comment
  (require '[clojure.core.async :as async :refer [go <! <!! alts!!]])

  (expect Double (+ 1 1))
  (expect (I ArithmeticException (ExMsg "Divide by zero")) (/ 1 0) :throws true)
  (expect ArithmeticException (/ 1 2) :throws true)
  (expect 3 (future 3) :async true)
  (expect 3 (future (Thread/sleep 200) 3) :async true :timeout 100)
  (expect 3 (go 3) :async true)
  (expect 3 (go (<! (async/timeout 200)) 3) :async true :timeout 100)
  (expect 3 (Thread/sleep 200) :timeout 100)
  (describe "conj"
    (expect [1 2] (conj [1] 2))
    (expect [1 2 3] (conj [1 2] 2))
    (expect [1 2] (conj nil 1 2))
    (expect (list 1 2) (conj (list 1) 2))))
