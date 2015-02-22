(ns expect.protocols)

(defprotocol Testable
  (teste [this] "Test expectations. Should return a state fn."))

(defprotocol Countable
  (counte [this] "Count expectations."))

(defprotocol Async
  (takev [this timeout]
    "Take a value from an async source, while handling timeouts."))
