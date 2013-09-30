;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.fca.exploration
  (:use conexp.base
        conexp.fca.contexts
        conexp.fca.implications
        conexp.fca.exploration.util
        conexp.fca.exploration.repl))

(ns-doc
 "Provides function for exploration and computing proper premises.")

;;; Exploration Interface

(declare explore-attributes-with-complete-counterexamples
         explore-attributes-with-incomplete-counterexamples
         default-handler-for-complete-counterexamples
         default-handler-for-incomplete-counterexamples)

(defn explore-attributes
  "Performs attribute exploration on the given context(s).  Returns a hashmap of
  implications computed and the final context, stored with keys :implications
  and :context (in the case of complete counterexamples)
  or :possible-context/:certain-context (in the case of incomplete counterexamples),
  respectively.

  Arguments are passed as keyword arguments like so

    (explore-attributes
      :context ctx-1
      :handler my-handler)

    (explore-attributes
      :incomplete-counterexamples true
      :possible-context ctx-1
      :certain-context  ctx-2
      :handler          my-other-handler
      :background-knowledge #{})

  Either a value for :context or values for :possible-context and :certain-context must be
  given, but not both.  The second option is only possible if :incomplete-counterexamples
  is set to «true».

  Optional keyword arguments are:

  - :handler «fn»

    Interaction is accomplished via the given handler fn, which is called with the current
    context of possible incidences, the current context of certain indcidences, all
    implications known so far and a new implication. The handler has to return
    counterexamples for the new implication, if there are some. Otherwise it has to return
    nil. Counterexamples are given as a sequence of rows, every row being of the form

       [g positive-attributes negative-attributes],

    where «g» is a new object, «positive-attributes» is a sequence of attributes the new
    object has, and «negative-attributes» is a sequence of attributes the new object does
    not have.  Note that «positive-attributes» and «negative-attributes» must be disjoint.

  - :background-knowledge «set of implications»

    background-knowledge denotes a set of implications used as background knowledge, which
    will be subtracted from the computed result.

  - :incomplete-counterexamples «true or false»

    Specifies whether incomplete counterexamples are allowed or not.  Default is false.
    Mandatory to be set to true if context is given via :possible-context
    and :certain-context.

  If you want to use automorphisms of the underlying context, you have to construct a
  special handler using the «make-handler» function. See the corresponding documentation
  of «make-handler»."  [& {:keys [possible-context certain-context context
  background-knowledge handler incomplete-counterexamples]}]
  ;; first check for arguments
  (assert (or (and (nil? context)
                   (not (nil? possible-context))
                   (not (nil? certain-context)))
              (and (not (nil? context))
                   (nil? possible-context)
                   (nil? certain-context)))
          "Contexts can only be specified by either specifying only :context or by
          specifying only both :possible-context and :certain-context.")
  (assert (contains? #{true false nil} incomplete-counterexamples)
          "Value for :incomplete-counterexamples must be either «true» or «false».")
  (assert (or incomplete-counterexamples
              (not possible-context))
          "Possible/certain incidences can only be specified if incomplete counterexamples
          are allowed.")
  (let [[possible-context certain-context] (if context
                                             [context context]
                                             [possible-context certain-context]),
        background-knowledge               (or background-knowledge #{})]
    ;; additional checks
    (assert (and (context? possible-context)
                 (context? certain-context))
            "Arguments to :context or :possible-context/:certain-context must be contexts")
    (assert (set? background-knowledge)
            "Background knowledge must be given as set")
    (assert (= (attributes certain-context)
               (attributes possible-context))
            "Given contexts must coincide on the set of attributes")
    (assert (= (objects certain-context)
               (objects possible-context))
            "Given contexts must coincide on the set of objects")
    ;; dispatch
    (if incomplete-counterexamples
      (explore-attributes-with-incomplete-counterexamples
         possible-context
         certain-context
         background-knowledge
         (or handler
             default-handler-for-incomplete-counterexamples))
      (explore-attributes-with-complete-counterexamples
         possible-context ; same as certain-context
         background-knowledge
         (or handler
             default-handler-for-complete-counterexamples)))))

;;; Handler for Expert Interaction

(defnk make-handler
  "Creates a handler for attribute exploration. Valid keys are

  - automorphisms: A sequence of automorphisms of the overall context,
    used to construct more examples from a given one.

    Currently, this has only an effect if the counterexamples are complete.

  - incomplete-counterexamples?: If true, allows for incomplete counterexamples.  In
    contrast to the case of complete counterexamples, the function returned takes four
    arguments (instead of 3), namely the context of the possible incidence, the context of
    the certain incidence, the known implications aas well as the current implication to
    be asked to the expert."
  [:automorphisms #{}, :incomplete-counterexamples? false]
  (assert (or (set? automorphisms) (seq? automorphisms)))
  (assert (or (contains? #{true false} incomplete-counterexamples?)))
  (if incomplete-counterexamples?
    ;; case of incomplete counterexamples
    (fn [possible-ctx certain-ctx known impl]
      (when-not (yes-or-no? (str "Does the implication " (print-str impl) " hold? "))
        (loop [counterexamples []]
          (let [counterexamples (conj counterexamples
                                      (incomplete-counterexample-via-repl possible-ctx
                                                                          certain-ctx
                                                                          known
                                                                          impl))]
            (if (yes-or-no? "Do you want to give another counterexample? ")
              (recur counterexamples)
              counterexamples)))))
    ;; case of complete counterexamples
    (fn [ctx known impl]
      (when-not (yes-or-no? (str "Does the implication " (print-str impl) " hold? "))
        (loop [counterexamples []]
          (let [counterexample (counterexample-via-repl ctx known impl),
                new-counters   (into counterexamples
                                     (examples-by-automorphism ctx counterexample automorphisms))]
            (if (yes-or-no? "Do you want to give another counterexample? ")
              (recur new-counters)
              new-counters)))))))

(let [dh (make-handler)]
  (defn default-handler-for-complete-counterexamples
    "Default handler for attribute exploration. Does it's interaction on the console."
    [ctx known impl]
    (dh ctx known impl)))

(let [dh (make-handler :incomplete-counterexamples? true)]
  (defn default-handler-for-incomplete-counterexamples
    "Default handler for attribute exploration with incomplete counterexamples. Does it's
    interaction on the console."
    [possible-ctx certain-ctx known impl]
    (dh possible-ctx certain-ctx known impl)))

;;; Attribute Exploration with Complete Counterexamples

(defn- explore-attributes-with-complete-counterexamples
  "Performs attribute exploration with complete background knowledge"
  [ctx background-knowledge handler]
  (loop [implications background-knowledge,
         last         (close-under-implications implications #{}),
         ctx          ctx]
    (if (not last)
      {:implications (difference implications background-knowledge),
       :context ctx}
      (let [conclusion-from-last (context-attribute-closure ctx last)]
        (if (= last conclusion-from-last)
          (recur implications
                 (next-closed-set (attributes ctx)
                                  (clop-by-implications implications)
                                  last)
                 ctx)
          (let [new-impl        (make-implication last conclusion-from-last),
                counterexamples (try
                                  (handler ctx implications new-impl)
                                  (catch Throwable e
                                    :abort))]
            (cond
             (= counterexamples :abort)
             (recur implications nil ctx) ; forget all other sets
             ;;
             counterexamples
             (let [new-objs (map first counterexamples)]
               ;; check that new names are not there already
               (when (exists [g new-objs] (contains? (objects ctx) g))
                 (illegal-argument "Got objects as «new objects» in exploration "
                                   "which are already present."))
               (recur implications
                      last
                      (make-context (into (objects ctx) new-objs)
                                    (attributes ctx)
                                    (union (incidence ctx)
                                           (set-of [g m] [[g ms] counterexamples,
                                                          m ms])))))
             ;;
             true                       ; add counterexample
             (recur (conj implications new-impl)
                    (next-closed-set (attributes ctx)
                                     (clop-by-implications (conj implications new-impl))
                                     last)
                    ctx))))))))

;;; Attribute Exploration with Incomplete Counterexamples

(defn- explore-attributes-with-incomplete-counterexamples
  "Performs attribute exploration allowing for incomplete counterexamples"
  [possible-ctx certain-ctx background-knowledge handler]
  (loop [implications background-knowledge,
         last         (close-under-implications implications #{}),
         possible-ctx possible-ctx
         certain-ctx  certain-ctx]
    (if (not last)
      {:implications     (difference implications background-knowledge),
       :possible-context possible-ctx
       :certain-context  certain-ctx}
      (let [conclusion-from-last (oprime possible-ctx (aprime certain-ctx last))] ; ?
        (if (= last conclusion-from-last)
          (recur implications
                 (next-closed-set (attributes possible-ctx)
                                  (clop-by-implications implications)
                                  last)
                 possible-ctx
                 certain-ctx)
          (let [new-impl        (make-implication last conclusion-from-last),
                counterexamples (try
                                  (handler possible-ctx certain-ctx implications new-impl)
                                  (catch Throwable e
                                    :abort))]
            (cond
             (= counterexamples :abort) ; abort exploration
             (recur implications nil possible-ctx certain-ctx)
             ;;
             counterexamples          ; add counterexample
             (let [new-objs (map first counterexamples)]
               ;; check that new names are not there already
               (when (exists [g new-objs] (contains? (objects possible-ctx) g))
                 (illegal-argument "Got objects as «new objects» in exploration "
                                   "which are already present."))
               (recur implications
                      last
                      ;; possible incidence, i.e. the one not excluded by the expert
                      (make-context (into (objects possible-ctx) new-objs)
                                    (attributes possible-ctx)
                                    (union (incidence possible-ctx)
                                           (set-of [g m] [[g _ neg] counterexamples,
                                                          m (difference (attributes possible-ctx)
                                                                        (set neg))])))
                      ;; certain incidence, i.e. the one given by the expert
                      (make-context (into (objects certain-ctx) new-objs)
                                    (attributes certain-ctx)
                                    (union (incidence certain-ctx)
                                           (set-of [g m] [[g pos _] counterexamples,
                                                          m pos])))))
             ;;
             true                     ; add implication
             (let [new-implications (conj implications new-impl)]
               (recur new-implications
                      (next-closed-set (attributes possible-ctx)
                                       (clop-by-implications new-implications)
                                       last)
                      possible-ctx
                      (make-context (objects certain-ctx)
                                    (attributes certain-ctx)
                                    (set-of [g m] [g (objects certain-ctx),
                                                   m (close-under-implications
                                                      new-implications
                                                      (oprime certain-ctx #{g}))])))))))))))

;;;

nil
