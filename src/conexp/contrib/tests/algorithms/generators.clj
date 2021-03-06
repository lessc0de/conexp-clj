;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.contrib.tests.algorithms.generators
  (:use clojure.test
        conexp.contrib.algorithms.generators))

;;;

(deftest test-generators
  (is (= (generate-by (fn runner [x]
                        (doseq [i (range x)]
                          (yield i)))
                      10)
         (list 0 1 2 3 4 5 6 7 8 9))))

;;;

nil
