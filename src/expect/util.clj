(ns expect.util
  (:require [clojure.string :as string]
            [stch.glob :refer [match-glob]]))

(defn sum [xs]
  (reduce + 0 xs))

(defn init-ns-es [es e]
  (if es (conj es e) [e]))

(defn ns->sym [namespace]
  (if (instance? clojure.lang.Namespace namespace)
    (ns-name namespace)
    namespace))

(defn reconstruct-args [async throws timeout]
  (->> [(when async [:async true])
        (when throws [:throws true])
        (when (> timeout 0) [:timeout timeout])]
       (remove nil?)
       flatten))

(defn ^String repeat-str [n ^String s]
  (apply str (repeat n s)))

(defn ^String space [& args]
  (string/join " " args))

(defn ns-matches [ns-syms pattern]
  (for [ns-sym ns-syms
        :when (match-glob pattern (str ns-sym))]
    ns-sym))
