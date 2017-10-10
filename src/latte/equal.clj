(ns latte.equal
  "The formal definition of equality, and its basic properties."

  (:refer-clojure :exclude [and or not])

  (:require
   [latte-kernel.syntax :as stx]
   [latte-kernel.norm :as norm]
   [latte-kernel.typing :as ty]
   [latte.utils :refer [decomposer]]
   [latte.core :as latte :refer [definition defthm defimplicit defimplicit*
                                          assume have qed proof]]
   [latte.prop :as p :refer [<=> and or not]]))

(definition equal%
  "The intuitionistic, second-order definition of equality.
This corresponds to Leibniz's *indiscernibility of identicals*."
  [[T :type] [x T] [y T]]
  (forall [P (==> T :type)]
    (<=> (P x) (P y))))

(defimplicit equal
  "Equality of `x` and `y` (which must be of the same type `T`).
This is an implicit version of [[equal%]]."
  [def-env ctx [x x-ty] [y y-ty]]
  (list #'equal% x-ty x y))

(defn decompose-equal-type [def-env ctx t]
  (decomposer
   (fn [t]
     (if (clojure.core/and (seq t)
                           (= (count t) 4)
                           (= (first t) #'latte.equal/equal%))
       [(second t) (nth t 2) (nth t 3)]
       ;; XXX: cannot decompose further because
       ;; we cannot retrieve the x and y of the
       ;; definition ... add dummy witnesses ?
       (throw (ex-info "Cannot infer an equal-type" {:type t}))))
   def-env ctx t))

(defthm eq-refl%
  "The reflexivity property of equality."
  [[T :type] [x T]]
  (equal% T x x))

(proof 'eq-refl% :script
  (assume [P (==> T :type)]
    (have <a> (<=> (P x) (P x)) :by (p/iff-refl (P x))))
  (qed <a>))

(defimplicit eq-refl
  "Equality is reflexive."
  [def-env ctx [x x-ty]]
  (list #'eq-refl% x-ty x x))

(defthm eq-sym%
  "The symmetry property of equality."
  [[T :type] [x T] [y T]]
  (==> (equal% T x y)
       (equal% T y x)))

(proof 'eq-sym% :script
  (assume [Heq (equal x y)
           P (==> T :type)]
    (have <a> (<=> (P x) (P y)) :by (Heq P))
    (have <b> (<=> (P y) (P x)) :by (p/iff-sym <a>)))
  (qed <b>))

(defimplicit eq-sym
  "Symmetry of equality, an implicit version of [[eq-sym%]]."
  [def-env ctx [eq-term eq-ty]]
  (let [[T x y] (decompose-equal-type def-env ctx eq-ty)]
    [(list #'eq-sym% T x y) eq-term]))

(defthm eq-trans%
  "The transitivity property of equality."
  [[T :type] [x T] [y T] [z T]]
  (==> (equal% T x y)
       (equal% T y z)
       (equal% T x z)))

(proof 'eq-trans% :script
  (assume [H1 (equal x y)
           H2 (equal y z)
           P (==> T :type)]
    (have <a> (<=> (P x) (P y)) :by (H1 P))
    (have <b> (<=> (P y) (P z)) :by (H2 P))
    (have <c> (<=> (P x) (P z))
          :by (p/iff-trans <a> <b>)))
  (qed <c>))

(defimplicit eq-trans
  "Transitivity of `equal`, an implicit version of [[eq-trans%]]."
  [def-env ctx [eq-term1 ty1] [eq-term2 ty2]]
  (let [[T1 x1 y1] (decompose-equal-type def-env ctx ty1)
        [T2 x2 y2] (decompose-equal-type def-env ctx ty2)]
    ;; XXX: these are redundant and probably take time ...
      #_(when-not (norm/beta-eq? def-env ctx T1 T2)
          (throw (ex-info "Equal type mismatch"
                          {:special 'latte.prop/eq-trans%
                           :left-type T1
                           :right-type T2})))
      #_(when-not (norm/beta-eq? def-env ctx y1 x2)
          (throw (ex-info "Term in the middle mismatch"
                          {:special 'latte.prop/eq-trans%
                           :left-rhs-term y1
                           :right-lhs-term x2})))
      [[(list #'eq-trans% T1 x1 y1 y2) eq-term1] eq-term2]))

(defimplicit* eq-trans*
  "Transitivity of `equal`, a n-ary version of [[eq-trans]].
The parameter `eq-terms` is a vector of at least two equalities.

For example:

```
(eq-trans* eq1 eq2)
==> (eq-trans eq1 eq2)

(eq-trans* eq1 eq2 eq3)
==> (eq-trans (eq-trans eq1 eq2) eq3)

(eq-trans* eq1 eq2 eq3 eq4)
==> (eq-trans (eq-trans (eq-trans eq1 eq2) eq3) eq4)
````
etc.
`"
  [def-env ctx & eq-terms]
  ;; (println "[eq-trans*] eq-terms=" eq-terms)
  ;; (when-not (and (seq eq-terms) (>= (count eq-terms) 2))
  ;;   (println "  ==> here 1")
  ;;   (throw (ex-info "There must be at least two arguments."
  ;;                   {:special 'latte.prop/eq-trans*
  ;;                    :arg eq-terms})))
  (let [[T x1 y1] (decompose-equal-type def-env ctx (second (first eq-terms)))]
    (loop [eq-terms' (rest eq-terms), x1 x1, y1 y1, ret (ffirst eq-terms)]
      (if (seq eq-terms')
        (let [[_ x2 y2] (decompose-equal-type def-env ctx (second (first eq-terms')))]
          (recur (rest eq-terms') x1 y2
                 [[(list #'eq-trans% T x1 y1 y2) ret] (ffirst eq-terms')]))
        ;; we're done
        (do
          ;; (println "  ==> ret=" ret)
          ret)))))

;; (defthm test-eq-trans
;;   [[T :type] [a T] [b T] [c T] [d T]]
;;   (==> (equal T a b)
;;        (equal T b c)
;;        (equal T c d)
;;        (equal T a d)))

;; (proof test-eq-trans
;;     :script
;;   (assume [H1 (equal T a b)
;;            H2 (equal T b c)
;;            H3 (equal T c d)]
;;      (have <a> (equal T a d)
;;            :by (eq-trans* H1 H2 H3))
;;     ;; (have <a> (equal T a c)
;;     ;;       :by (((eq-trans T a b c) H1) H2))
;;     ;; (have <b> (equal T a d)
;;     ;;       :by (((eq-trans T a c d) <a>) H3))
;;     (qed <a>)))

(defthm eq-subst%
  "Substitutivity property of equality."
  [[T :type] [P (==> T :type)] [x T] [y T]]
  (==> (equal% T x y)
       (P x)
       (P y)))

(proof 'eq-subst% :script
  (assume [H1 (equal x y)
           H2 (P x)]
    (have <a> (<=> (P x) (P y)) :by (H1 P))
    (have <b> (P y) :by ((p/iff-elim-if <a>) H2)))
  (qed <b>))

(defimplicit eq-subst
  "Substitutivity of `equal`, an implicit version of [[eq-subst]]."
  [def-env ctx [P P-type] [eq-term eq-type] [Px Px-type]]
  (let [[T x y] (decompose-equal-type def-env ctx eq-type)]
    [[(list #'eq-subst% T P x y) eq-term] Px]))

(defthm eq-cong%
  "Congruence property of equality."
  [[T :type] [U :type] [f (==> T U)] [x T] [y T]]
  (==> (equal% T x y)
       (equal% U (f x) (f y))))

(proof 'eq-cong% :script
  (assume [H1 (equal x y)
           Q (==> U :type)]
    (assume [H2 (Q (f x))]
            (have <a1> _ :by (eq-subst% T (lambda [z T] (Q (f z))) x y))
            (have <a> (Q (f y)) :by (<a1> H1 H2))
            [:print-type '<a>])
    (have <b> (equal y x) :by (eq-sym H1))
    [:print-type '<b>]
    (assume [H3 (Q (f y))]
      (have <c1> _ :by (eq-subst% T (lambda [z T] (Q (f z))) y x))
      (have <c> (Q (f x)) :by (<c1> <b> H3)))
    (have <d> (<=> (Q (f x)) (Q (f y))) :by (p/iff-intro <a> <c>)))
  (qed <d>))

(defimplicit eq-cong
  "Congruence of `equal`, an implicit version of [[eq-cong]]."
  [def-env ctx [f f-ty] [eq-term eq-ty]]
  (let [[T x y] (decompose-equal-type def-env ctx eq-ty)
        [T' U] (p/decompose-impl-type def-env ctx f-ty)]
    [(list #'eq-cong% T U f x y) eq-term]))




