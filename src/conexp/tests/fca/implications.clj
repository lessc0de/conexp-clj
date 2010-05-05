;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.tests.fca.implications
  (:use clojure.test)
  (:use conexp.base
	conexp.fca.contexts
	conexp.fca.implications)
  (:require [conexp.tests.fca.contexts :as contexts]))

;;;

(deftest test-make-implication
  (is (make-implication #{} #{}))
  (is (make-implication [] []))
  (is (make-implication () ()))
  (is (make-implication [1 2 3] '[a b c])))

(deftest test-Implication-equals
  (is (= (make-implication [] []) (make-implication [] [])))
  (is (= (make-implication [1] [2]) (make-implication [1] [1 2])))
  (is (= (make-implication '[a] '[b c d]) (make-implication '#{a} '#{b c d}))))

;;;

(defvar- *testing-data*
  [(make-implication [] []),
   (make-implication [1 2 3] '[a b c]),
   (make-implication [1 2 3] '[3 a b c])
   (make-implication #{1 2 3 4 5} #{3 4 5 6 7})])

(deftest test-Implication-hashCode
  (with-testing-data [impl-1 *testing-data*,
                      impl-2 *testing-data*]
    (=> (= impl-1 impl-2)
        (= (hash impl-1) (hash impl-2)))))

;;;

;; holds?
;; respects?
;; add-immediate-elements (private)
;; close-under-implications (private)
;; clop-by-implications
;; follows-semantically
;; minimal-implication-set?
;; add-immediate-elements* (private)
;; clop-by-implications*

(deftest test-stem-base
  (is (= 1 (count (stem-base (one-context #{1 2 3 4 5})))))
  (doseq [ctx [contexts/*test-ctx-01*,
               contexts/*test-ctx-04*
               contexts/*test-ctx-07*,
               contexts/*test-ctx-08*]]
    (let [sb (stem-base ctx)]
      (is (minimal-implication-set? sb))
      (is (forall [impl sb] (holds? impl ctx)))
      (is (forall [A (subsets (attributes ctx)),
                   B (subsets (difference (attributes ctx) A))]
            (let [impl (make-implication A B)]
              (=> (holds? impl ctx)
                  (follows-semantically? impl sb))))))))

;;;

nil
