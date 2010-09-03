;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.tests.io.contexts
  (:use conexp.base
        conexp.fca.contexts
        conexp.io.contexts
        conexp.tests.io.util)
  (:use clojure.test))

;;;

(defvar- *contexts-oi*
  [(make-context #{"a" "b" "c"}
                 #{"1" "2" "3"}
                 #{["a" "1"] ["a" "3"]
                   ["b" "2"] ["c" "3"]}),
   (null-context #{})]
  "Context to use for out-in testing")

(deftest test-context-out-in
  (with-testing-data [ctx *contexts-oi*,
                      fmt (list-context-formats)]
    (try (= ctx (out-in ctx 'context fmt))
         (catch IllegalArgumentException _ true))))

;;

(defvar- *contexts-oioi*
  [(make-context #{1 2 3} #{4 5 6} <),
   (make-context #{'a} #{'+} #{['a '+]})]
  "Contexts to use for out-in-out-in testing")

(deftest test-context-out-in-out-in
  (with-testing-data [ctx *contexts-oioi*,
                      fmt (list-context-formats)]
    (try (out-in-out-in-test ctx 'context fmt)
         (catch IllegalArgumentException _ true))))

;;

(defvar- *contexts-with-empty-columns*
  [(null-context #{1 2 3 4}),
   (null-context #{1 2 3}),
   (null-context #{}),
   (make-context #{1 2 3} #{1 2 3} #{[1 2] [2 3] [3 2]})]
  "Context with empty columns, to test for corner cases")

(deftest test-empty-columns
  (with-testing-data [ctx *contexts-with-empty-columns*,
                      fmt (list-context-formats)]
    (try (out-in-out-in-test ctx 'context fmt)
         (catch IllegalArgumentException _ true))))

;;

(deftest test-for-random-contexts
  (with-testing-data [ctx (random-contexts 20 50),
                      fmt (list-context-formats)]
    (try (out-in-out-in-test ctx 'context fmt)
         (catch IllegalArgumentException _ true))))

;;;

nil