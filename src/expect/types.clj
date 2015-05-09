(ns expect.types
  (:require [expect.protocols :as proto]
            [annotate.types :refer [U I Protocol Symbol Any Seqable]]))

(def NsOrSym (U Symbol clojure.lang.Namespace))
(def Level (U :ex :ns))
(def TestableCountable
  (I (Protocol proto/Testable) (Protocol proto/Countable)))
(def Errors (Seqable [Any Any]))
(def NsErrors (Seqable [Symbol Errors]))
