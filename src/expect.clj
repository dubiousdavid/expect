(ns expect
  (:use [rk.annotate :only [validate]]
        [clojure.core.match :only [match]])
  (:require rk.annotate.types
            [expect.colors :as color]))

(def ^{:dynamic true :private true} *top-level* true)
(def ^{:dynamic true :private true} *padding* 0)
(def ^{:private false} expectations (atom {}))

(defprotocol Testable
  (teste [this] "Test expectation."))

(defn- init-ns-es [es e]
  (if es (conj es e) [e]))

(defn adde [e]
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
                [code error])))))

(defn- reconstruct-args [async throws timeout]
  (->> [(when async [:async true])
        (when throws [:throws true])
        (when (> timeout 0) [:timeout timeout])]
       (remove nil?)
       flatten))

(defmacro expect [expected actual & {:keys [async timeout throws]
                                     :or {async false, timeout 0, throws false}}]
  (let [actual' (if async
                  (if (> timeout 0)
                    `(deref ~actual ~timeout [::timeout ~timeout])
                    `(deref ~actual))
                  actual)
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
        [description errors]))))

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

(defn- format-error-totals [num-errors]
  (if (> num-errors 0)
    (color/magenta (str "Result: " num-errors " error(s) found"))
    (color/green "Result: no errors found")))

(defn run-ns [ns-sym]
  (let [num-errors (atom 0)
        errors (test-ns ns-sym)]
    (when (seq errors)
      (swap! num-errors + (count errors))
      (prn)
      (print-errors errors)
      (prn))
    (println (format-error-totals @num-errors))))

(defn rerun-ns [ns-sym]
  (clear-ns ns-sym)
  (require :reload ns-sym)
  (test-ns ns-sym))

(defn run-all []
  (let [num-errors (atom 0)]
    (doseq [[ns-sym errors] (test-all)]
      (when (seq errors)
        (swap! num-errors + (count errors))
        (prn)
        (pr-data (color/cyan ns-sym))
        (pad-left (print-errors errors))
        (prn)))
    (println (format-error-totals @num-errors))))

(comment
  (expect Double (+ 1 1))
  (expect (I ArithmeticException (ExMsg "Divide by zero")) (/ 1 0) :throws true)
  (expect ArithmeticException (/ 1 2) :throws true)
  (expect 3 (future 3) :async true)
  (expect 3 (future (Thread/sleep 200) 3) :async true :timeout 100)
  (describe "conj"
    (expect [1 2] (conj [1] 2))
    (expect [1 2] (conj nil 1 2))))
