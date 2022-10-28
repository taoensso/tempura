(ns taoensso.tempura-tests
  (:require
   [clojure.test     :as test :refer [deftest testing is]]
   [clojure.string   :as str]
   [taoensso.encore  :as enc]
   [taoensso.tempura :as tempura]
   [taoensso.tempura.impl :as impl]))

(comment
  (remove-ns      'taoensso.tempura-tests)
  (test/run-tests 'taoensso.tempura-tests))

;;;; Impl

(deftest _impl_str->vargs-fn
  [(is (enc/throws? (impl/str->vargs-fn nil)))
   ;; (is (enc/throws? ((impl/str->vargs-fn "%") "non-vector input")))
   (is (= "hi"      ((impl/str->vargs-fn "hi")         [])))
   (is (= "hi"      ((impl/str->vargs-fn "hi")         ["unused arg"])))
   (is (= "hi %1"   ((impl/str->vargs-fn "hi `%1")     [])))
   (is (= "hi a1"   ((impl/str->vargs-fn "hi %1")      ["a1"])))
   (is (= "hi :a1"  ((impl/str->vargs-fn "hi %1")      [:a1]))) ; `str` called on args
   (is (= "hi "     ((impl/str->vargs-fn "hi %1")      [])))    ; insufficient args
   (is (= "hi nil"  ((impl/str->vargs-fn "hi %1" #(if (nil? %) "nil" %)) []))) ; nil-patch
   (is (= "a1, hi"  ((impl/str->vargs-fn "%1, hi") ["a1"]))) ; "" start with arg
   (is (= "hi :1 :2 :1 :3 :1 %1 :1 %3 :11 a:2b%end % %s"
         ((impl/str->vargs-fn "hi %1 %2 %1 %3 %1 `%1 %1 `%3 %11 a%2b`%end % %s")
          [:1 :2 :3 :4])))])

(deftest  _impl_vec->vargs-fn
  [(is (enc/throws? (impl/vec->vargs-fn nil)))
   ;; (is (enc/throws? ((impl/vec->vargs-fn ['%1]) "non vector input")))
   (is (= ["hi"]      ((impl/vec->vargs-fn ["hi"])     [])))
   (is (= ["hi"]      ((impl/vec->vargs-fn ["hi"])     ["unused arg"])))
   (is (= ["hi" "a1"] ((impl/vec->vargs-fn ["hi" '%1]) ["a1"])))
   (is (= ["hi" :a1]  ((impl/vec->vargs-fn ["hi" '%1]) [:a1]))) ; Arb args
   (is (= ["hi" nil]  ((impl/vec->vargs-fn ["hi" '%1]) [])))    ; Insufficient args
   (is (= ["hi" [:strong :1] ", " :1 :2]
         ((impl/vec->vargs-fn ["hi" [:strong '%1] ", " '%1 '%2]) [:1 :2 :3])))
   (is (= ["a1" ", hi"] ((impl/vec->vargs-fn ['%1 ", hi"]) ["a1"]))) ; Start with arg
   (is (= ["hi" {:attr "foo %1"}] ; Don't touch attrs
         ((impl/vec->vargs-fn ["hi" {:attr "foo %1"}]) ["a1"])))])

(deftest _impl_expand-locales
  [(is (= (impl/expand-locales [:en-us-var1     :fr-fr :fr :en-gd :de-de]) [[:en-us-var1 :en-us :en] [:fr-fr :fr] [:de-de :de]]))
   (is (= (impl/expand-locales [:en :en-us-var1 :fr-fr :fr :en-gd :de-de]) [[:en]                    [:fr-fr :fr] [:de-de :de]])) ; Stop :en-* after base :en
   (is (= (impl/expand-locales [:en-us :fr-fr :en])                        [[:en-us :en]             [:fr-fr :fr]])) ; Never change langs before vars
   ])

;;;; Core

(deftest _compact
  (let [compact @#'tempura/compact]
    [(is (= (compact [:span "a" "b" [:strong "c" "d"] "e" "f"]) [:span "ab" [:strong "cd"] "ef"]))]))

(deftest _get-default-resource-compiler
  (let [rc (tempura/get-default-resource-compiler
             {:experimental/compact-vectors? #_false true})]

    [(is (= ((rc "Hi %1 :-)")     ["Steve"])   "Hi Steve :-)"))
     (is (= ((rc "Hi **%1** :-)") ["Steve"])   "Hi **Steve** :-)"))
     (is (= ((rc ["a **b %1 c** d %2"]) [1 2]) [:span "a " [:strong "b 1 c"] " d 2"]))
     (is (= ((rc ["a" "b"]) [])                "ab"))]))

;;;; High-level

(deftest _tr-search-order
  (let [base-opts
        {:default-locale :ld
         :dict
         {:l1 {:r1 "l1/r1" :r2 "l1/r2" :missing "l1/?"}
          :l2 {:r1 "l2/r1" :r2 "l2/r2" :missing "l2/?"}
          :ld {:r1 "ld/r1" :r2 "ld/r2" :missing "ld/?"}}}

        tr
        (fn [opts & args]
          (apply tempura/tr
            (enc/nested-merge base-opts opts)
            args))]

    [(is (= (tr {}                              [:l1 :l2] [:r1 :r2]) "l1/r1"))
     (is (= (tr {:dict {:l1 {:r1 nil        }}} [:l1 :l2] [:r1 :r2]) "l1/r2"))
     (is (= (tr {:dict {:l1 {:r1 nil :r2 nil}}} [:l1 :l2] [:r1 :r2]) "l2/r1"))
     (is (= (tr {:dict {:l1 nil :l2 {:r1 nil}}} [:l1 :l2] [:r1 :r2]) "l2/r2"))
     (is (= (tr {:dict {:l1 nil :l2 nil}}       [:l1 :l2] [:r1 :r2]) "ld/r1"))
     (is (= (tr {:dict {:l1 nil :l2 nil}
                 :default-locale nil}           [:l1 :l2] [:r1 :r2]) nil))

     (is (= (tr {:dict {:l1 {:r1 nil :r2 nil}
                        :l2 nil}
                 :default-locale nil}           [:l1 :l2] [:r1 :r2]) "l1/?"))

     (is (= (tr {:dict {:l1 nil :l2 nil}} [:l1 :l2] [:r1 :r2 "fb"]) "ld/r1"))
     (is (= (tr {:dict {:l1 nil :l2 nil}
                 :default-locale nil}     [:l1 :l2] [:r1 :r2 "fb"]) "fb"))]))

(deftest _tr-resource-args
  [(is (= (tempura/tr {:dict {:l1 {:r1 "a %1 %1 b %2 %1 c %3"}}} [:l1] [:r1])           "a   b   c "))
   (is (= (tempura/tr {:dict {:l1 {:r1 "a %1 %1 b %2 %1 c %3"}}} [:l1] [:r1] [1 2])     "a 1 1 b 2 1 c "))
   (is (= (tempura/tr {:dict {:l1 {:r1 "a %1 %1 b %2 %1 c %3"}}} [:l1] [:r1] [1 2 3 4]) "a 1 1 b 2 1 c 3"))
   (is (= (tempura/tr {:dict {:l1 {:r1 "%1% (percent)"}}}        [:l1] [:r1] [75])      "75% (percent)"))])

(deftest _tr-resource-compilation
  [(is (= (tempura/tr {} [] ["hello"])             "hello"))
   (is (= (tempura/tr {} [] ["hello **world**"])   "hello **world**"))
   (is (= (tempura/tr {} [] [["hello **world**"]]) [:span "hello " [:strong "world"]]))])

;;;;

#?(:cljs
   (defmethod test/report [:cljs.test/default :end-run-tests] [m]
     (when-not (test/successful? m)
       ;; Trigger non-zero `lein test-cljs` exit code for CI
       (throw (ex-info "ClojureScript tests failed" {})))))

#?(:cljs (test/run-tests))
