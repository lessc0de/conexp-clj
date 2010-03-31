;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.util.multimethods
  (:use [clojure.contrib.string :only (join)]
    conexp.util))

;;; multimethod helpers

(defn test-list-types-object
  "Tests whether the second argument is of a child type of the elements of the
  first argument, returns the first matching parent type. Returns nil if
  the second argument is of none of the types in the list.

  Parameters:
    typelist   _list of types
    object     _object to test for 'isa?' relation"
  [typelist object]
  (let [ is-child-type? (fn [x] (isa? (type object) x))
         good-types (filter is-child-type? typelist) ]
    (first good-types)))

(defn insert-before-first-pred
  "Takes a predicate, a seq and an element and returns a seq
  that will have the element inserted right before the first
  element that fulfills the predicate.

  Parameters:
         pred   _predicate function
         coll   _sequence
         item   _new item"
  [pred coll item]
  (loop [ construct identity
          coll coll]
    (if (empty? coll) (construct (seq (list item)))
      (let [ head (first coll)
             tail (rest coll) ]
        (if (pred head) (construct (cons item coll))
          (recur (fn [x] (construct (cons head x))) tail))))))

(defmacro get-value-if-defined
  "Takes a quoted symbol name and returns its value if defined
   in the current namespace, or nil else"
  [symbol]
  `(let [ v# (ns-resolve *ns* ~symbol) ]
     (when v# (var-get v#))))

(defmacro def-
  "Private version of def"
  [name & exprs]
  `(do
     (def ~name ~@exprs)
     (alter-meta! (var ~name) conj {:private true})))

(defmacro in-other-ns
  "Takes a namespace name and an expression that will be run
  within the other namespace in an implicit do, then returns
  to the current namesapce"
  [ns & exprs]
  (let [ current-ns (ns-name *ns*) ]
    `(do 
       (in-ns ~ns)
       ~@exprs
       (in-ns (quote ~current-ns)))))
            
(defmacro inherit-multimethod
  "Creates a new multimethod that dispatches by type hierarchy
   on the first parameter and sets the dispatcher to recognize
   all inherited types, then appends the given documentation.
   Uses the value of *poly-ns* as namespace for the defmulti
   definitions, if defined, or the current namespace else."
  [name type-name doc-str]
  (let [ poly-ns (get-value-if-defined '*poly-ns*) ]
    (if poly-ns
      `(do 
         (in-other-ns (quote ~poly-ns)
           (inherit-multimethod-local ~name ~type-name ~doc-str))
         (clojure.core/refer (quote ~poly-ns) :only [(quote ~name)]
           :rename {(quote ~name) (quote temporary#)})
         (ns-unmap *ns* (quote ~name))
         (def ~name temporary#)
         (alter-meta! (var ~name) conj {:doc 
           (str "Please refer to:\n\t(doc " (quote ~poly-ns) 
             "/" (quote ~name) ")")})
         (ns-unmap *ns* (quote temporary#)))
      `(inherit-multimethod-local ~name ~type-name ~doc-str))))


(defmacro inherit-multimethod-local
  "Creates a new multimethod that dispatches by type hierarchy
   on the first parameter and sets the dispatcher to recognize
   all inherited types, then appends the given documentation."
  [name type-name doc-str]
  (let [name-str (str name)]
    `(let [ old-meta# (meta ((ns-map *ns*) (quote ~name)))
            old-doc-strings-val# (if (nil? old-meta#) {} (:doc-strings old-meta#))
            old-doc-strings# (if (nil? old-doc-strings-val#) {} old-doc-strings-val#)
            old-types# (if (nil? old-meta#) (list) (:type-list old-meta#))]
       (defonce ~name nil)
       (let [ old-func# ~name
              doc-strings# (conj old-doc-strings# 
                             {~type-name (str ~type-name "\n=====\n" ~doc-str)})
              is-new-type# (not (some #{~type-name} old-types#))
              types#  (if is-new-type# 
                        (insert-before-first-pred (fn [x#] (isa? ~type-name x#))
                          old-types# ~type-name)
                        old-types#)
              new-doc# (join "\n+++++\n\n" 
                         (cons (str "is a multimethod dispatched"
                                 " on type of the first argument...")
                           (map doc-strings# types#)))]
         (when is-new-type#
           (defmulti ~name (fn [x# & args#] (test-list-types-object types# x#)))
           (defmethod ~name nil [& args#] 
             (illegal-argument (str ~name-str 
                                 " called, but there is no method declared for type "
                                 (when-not (empty? args#) (type (first args#))) "!"  )))
           (defmethod ~name ~type-name [& args#] 
             (illegal-argument (str ~name-str 
                                 " called, but there is a method declared but"
                                 " not defined for type "
                                 (when-not (empty? args#) (type (first args#))) "!"  )))
           (doseq [t# old-types#]
             (defmethod ~name t# [& args#] (apply old-func# args#))))
         (.setMeta (var ~name) (conj (meta (var ~name)) 
                                 {:doc new-doc#
                                 :doc-strings doc-strings#
                                 :type-list types#}))))))
      