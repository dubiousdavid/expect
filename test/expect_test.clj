(ns expect-test
  (:require [expect :refer :all]
            [midje.sweet :refer :all]
            [clojure.core.async :as async :refer [go <!]]
            [annotate.types :refer [I ExMsg Eq]]))

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
  (describe "list"
    (expect (Eq (list 1 2)) (conj (list 1) 2))
    (expect (list 1 2) (conj (list 1) 2))))

(def result
  '[[expect-test
     [[(expect Double (+ 1 1)) (not (instance? Double 2))]
      [(expect ArithmeticException (/ 1 2) :throws true)
       "Expected a thrown exception. Actual: 1/2"]
      [(expect 3 (future (Thread/sleep 200) 3) :async true :timeout 100)
       "Timed out after 100 ms"]
      [(expect 3 (go (<! (async/timeout 200)) 3) :async true :timeout 100)
       "Timed out after 100 ms"]
      [(expect 3 (Thread/sleep 200) :timeout 100) "Timed out after 100 ms"]
      ["conj"
       [[(expect [1 2 3] (conj [1 2] 2)) [nil nil (not= 3 2)]]
        [(expect [1 2] (conj nil 1 2)) (not (vector? (2 1)))]
        ["list"
         [[(expect (Eq (list 1 2)) (conj (list 1) 2)) (not= (1 2) (2 1))]
          [(expect (list 1 2) (conj (list 1) 2)) ((not= 1 2) (not= 2 1))]]]]]]]])

(def ns-result
  '[[(expect Double (+ 1 1)) (not (instance? Double 2))]
    [(expect ArithmeticException (/ 1 2) :throws true)
     "Expected a thrown exception. Actual: 1/2"]
    [(expect 3 (future (Thread/sleep 200) 3) :async true :timeout 100)
     "Timed out after 100 ms"]
    [(expect 3 (go (<! (async/timeout 200)) 3) :async true :timeout 100)
     "Timed out after 100 ms"]
    [(expect 3 (Thread/sleep 200) :timeout 100) "Timed out after 100 ms"]
    ["conj"
     [[(expect [1 2 3] (conj [1 2] 2)) [nil nil (not= 3 2)]]
      [(expect [1 2] (conj nil 1 2)) (not (vector? (2 1)))]
      ["list"
       [[(expect (Eq (list 1 2)) (conj (list 1) 2)) (not= (1 2) (2 1))]
        [(expect (list 1 2) (conj (list 1) 2)) ((not= 1 2) (not= 2 1))]]]]]])

(fact "test-all"
  (test-all) => result)

(fact "ptest-all"
  (ptest-all) => result)

(fact "test-ns"
  (test-ns *ns*) => ns-result)

(fact "ptest-ns"
  (ptest-ns *ns*) => ns-result)
