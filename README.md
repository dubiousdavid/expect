# expect

Unit-testing library based on [annotate](https://github.com/roomkey/annotate) and ideas from various existing libraries.

## Purpose

* Describe the shape of the value returned by an expression using [annotate](https://github.com/roomkey/annotate).
* Test functions that should run within a specified amount of time.
* Test values that are asynchronous (e.g., futures and [core.async](https://github.com/clojure/core.async) channels).
* Run tests in parallel using multiple threads.
* Test output returns Clojure data structures by default. Functions are also provided that take that data and print it with color and indentation.

## Installation

```clojure
[com.2tothe8th/expect "0.1.0"]
```

See: https://github.com/dubiousdavid/lein-expect for a leiningen plugin.

## API Documentation

http://dubiousdavid.github.io/expect

## Usage

To create an expectation we call the `expect` macro passing in two mandatory fields: a type describing the shape of our data and an expression to test. The types can be any valid type from the annotate library. For example, we might pass a class such as String or Double, as seen below.  Please refer to the [extensive documentation](https://github.com/roomkey/annotate#annotate) on the annotate project page for more information.

In a REPL enter the following:

```clojure
(use 'expect.core)

(expect String (str "Hello, " "there"))
(expect Double (* 5 1.6))
(expect Double (+ 2 2))
```

When we're ready to test our expectations we can call `test-all`.

```clojure
(test-all)
```

You should see the following output:

```clojure
[[user [[(expect Double (+ 2 2)) (not (instance? Double 4))]]]]
```

For each namespace where expectations exist a vector of errors is returned indicating the expression tested and the error that occurred. In this case, `(+ 2 2)` is not of type Double, but Long. The other tests passed, so nothing is returned for them.

This is really nice, but we may want to render the results of our tests in a more human-readable manner. To do this we can call the print variation of `test-all`, called `run-all`. This renders the following:

![output-1](http://localhost:8000/images/output-1.png)

### Exceptions

To indicate that an expectation should throw an exception, pass `:throws true`. In the following example we are indicating that the expression should throw an ArithmeticException and have an exception message of "Divide by zero". We need to refer `annotate.types` to use the `I` and `ExMsg` functions.

```clojure
(use 'annotate.types)

(expect (I ArithmeticException (ExMsg "Divide by zero")) (/ 1 0) :throws true)
```

Calling `test-all` again produces the same output as before, since the previous test passed.

### Asynchronous values

To indicate that an expression returns an asynchronous value pass `:async true`. In the examples below we want to test the eventual value returned by a `future` and core.async channel to see if it's the number 3. In the third expectation we also want to test if a value is returned within 100ms. A failure to return a value within that timeframe will produce a failing test.

```clojure
(require '[clojure.core.async :as async :refer [go <!]])

(expect 3 (future 3) :async true)
(expect 3 (go 3) :async true)
(expect 3 (go (<! (async/timeout 200)) 3) :async true :timeout 100)
```

Calling `test-all` again produces the following output:

```clojure
[[user
  [[(expect Double (+ 2 2)) (not (instance? Double 4))]
   [(expect 3 (go (<! (async/timeout 200)) 3) :async true :timeout 100)
    "Timed out after 100 ms"]]]]
```

Notice that the first two asynchronous test passed, but the last one failed because if failed to produce a value within the expected timeframe of 100ms.

### Describe

To group multiple expectations together we can wrap them in a call to `describe`, passing a description as the first argument.

```clojure
(describe "conj"
  (expect [1 2] (conj [1] 2))
  (expect [1 2 3] (conj [1 2] 2))
  (expect [1 2] (conj nil 1 2)))
```

Calling `run-all` produces the following output. Notice that expectations within a call to `describe` are indented.

![output-2](http://localhost:8000/images/output-2.png)

### Running tests in parallel

All the commands to test expectations that we have seen have a parallel variation. The parallel variation is the name of the function with a "p" prepended. For example, `ptest-all` and `prun-all`.

In addition, you can specify the level at which the parallelism should occur. The default is at the namespace level, meaning all tests within a namespace will run in a single thread. So if you have four namespaces, your tests will run in four threads.

If you want to run all tests within their own thread, pass `:level :ex` to the function. This will spawn a new thread for each test.

To run our current set of expectations in their own thread we could run the following:

```clojure
(ptest-all :level :ex)
```

The output isn't any different from calling `test-all`. In practice, you will only see a benefit from running your tests in parallel if they are taking a sizable amount of time to complete.
