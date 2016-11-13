(ns taoensso.tempura
  "Pure Clojure/Script i18n translations library."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  #?(:clj
     (:require
      [clojure.string        :as str]
      [taoensso.encore       :as enc  :refer [have have? qb]]
      [taoensso.tempura.impl :as impl :refer []]))

  #?(:cljs
     (:require
      [clojure.string        :as str]
      [taoensso.encore       :as enc  :refer-macros [have have?]]
      [taoensso.tempura.impl :as impl :refer-macros []])))

(enc/assert-min-encore-version [2 86 1])

(def ^:dynamic *tr-opts*  nil)
(def ^:dynamic *tr-scope* nil)

(defmacro with-tr-opts  [opts  & body] `(binding [*tr-opts*  ~opts]  ~@body))
(defmacro with-tr-scope
  "`(with-tr-scope :foo.bar (tr _ _ [:baz]))` is equivalent to
   `(tr _ _ [:foo.bar/baz])`"
  [scope & body]
  `(binding [*tr-scope* ~scope] ~@body))

;;;;

(def ^:private get-default-resource-compiler
  "Good general-purpose resource compiler.
  Supports output of text, and Hiccup forms with simple Markdown styles."
  (enc/memoize_
    (fn [{:keys [escape-html?]}]
      (let [esc1 (if escape-html? impl/escape-html             identity)
            esc2 (if escape-html? impl/vec-escape-html-in-strs identity)]

        (enc/memoize_
          (fn [res] ; -> [(fn [vargs]) -> <compiled-resource>]
            (enc/cond! ; Nb no keywords, nils, etc.
              (fn?     res) (-> res) ; Completely arb, full control
              (string? res) (-> res esc1 impl/str->vargs-fn)
              (vector? res) (-> res
                              impl/vec->vtag
                              impl/vec-explode-styles-in-strs
                              impl/vec-explode-args-in-strs
                              esc2       ; Avoid for Reactjs
                              impl/vec->vargs-fn))))))))

(comment
  (let [rc (get-default-resource-compiler {})]
    [((rc  "Hi %1 :-)")  ["Steve"])
     ((rc  "Hi **%1**")  ["Steve"])
     ((rc ["Hi **%1**"]) ["Steve"])]))

(def default-tr-opts
  {:default-locale :en
   :dict {:en {:missing "[Missing tr resource]"}}
   :scope-fn (fn [] *tr-scope*)

   :cache-dict?      #?(:clj false :cljs true)
   :cache-locales?   #?(:clj false :cljs true)
   :cache-resources? false

   :resource-compiler (get-default-resource-compiler {:escape-html? false})
   :missing-resource-fn nil ; Nb return nnil to use as resource
   #_(fn [{:keys [opts locales resource-ids resource-args]}]
       (debugf "Missing tr resource: %s" [locales resource-ids])
       nil)})

(def example-dictionary
  {:en-GB ; Locale
   {:missing ":en-GB missing text" ; Fallback for missing resources
    :example ; You can nest ids if you like
    {:greet "Good day %1!" ; Note Clojure fn-style %1 args
     }}

   :en ; A second locale
   {:missing ":en missing text"
    :example
    {:greet "Hello %1"
     :farewell "Goodbye %1"
     :foo "foo"
     :bar "bar"
     :bar-copy :en.example/bar ; Can alias entries
     :baz [:div "This is a **Hiccup** form"]

     ;; Can use arbitrary fns as resources
     :qux (fn [[arg1 arg2]] (str arg1 " and " arg2))}

    :example-copy :en/example ; Can alias entire subtrees

    :import-example
    {:__load-resource ; Inline edn content loaded from disk/resource
     "resources/i18n.clj"}}})

(comment ; For README
  (tr {:dict example-dictionary}
    [:en] ; Vector of descending-preference locales to search
    [:example/foo] ; Vector of descending-preference resource-ids to search
    ) ; => "foo"

  (def opts {:dict example-dictionary})

  (tr opts [:en] [:example/invalid :example/bar]) ; => "bar"
  (tr opts [:en] [:example/invalid "Inline fallback"]) ; => "Inline fallback"

  (tr opts [:en] [:example/greet] ["Steve"]) ; => "Hello Steve"

  (tr opts [:en] [:example/invalid "Hi %1"] ["Steve"]) ; => "Hi Steve"

  (tr opts [:en] [:example/invalid [:div "My **fallback** div"]])
  ; [:div "My " [:strong "fallback"] " div"]

  (tr [["Please enter your **login details** below:"]]))

;;;;

(def ^:private merge-into-default-opts
  (enc/memoize_
    (fn [opts dynamic-opts]
      (merge default-tr-opts opts dynamic-opts))))

(def ^:private scoped
  (enc/memoize_
    (fn [locale ?scope resid]
      (enc/merge-keywords [locale ?scope resid]))))

(defn- search-resids*
  "loc1 res1 var1 var2 ... base
        res2 var1 var2 ... base
        ...
   loc2 res1 var1 var2 ... base
        res2 var1 var2 ... base
        ..."
  [dict locale-splits ?scope resids]
  (reduce
    (fn [acc locale-split]
      (reduce
        (fn [acc resid]
          (reduce
            (fn [acc lvar]
              ;; (debugf "Searching: %s" (scoped lvar ?scope resid))
              (when-let [res (get dict (scoped lvar ?scope resid))]
                (reduced (reduced (reduced #_[res resid] res)))))
            acc locale-split))
        acc resids))
    nil locale-splits))

(def ^:private search-resids*-cached (enc/memoize_ search-resids*))

(defn- search-resids [cache? dict locale-splits ?scope resids]
  (if cache?
    (search-resids*-cached dict locale-splits ?scope resids)
    (search-resids*        dict locale-splits ?scope resids)))

#_
(defmacro vargs "Experimental. Compile-time `impl/vargs`."
  [x]
  (if (map? x)
    (do
      (assert (enc/revery? enc/pos-int? (keys x))
        "All arg map keys must be +ive non-zero ints")
      (impl/vargs x))
    (have vector? x)))

#_(comment (macroexpand '(vargs {1 (do "1") 2 (do "2")})))

;;;;

(let [;;; Local aliases to avoid var deref
      merge-into-default-opts merge-into-default-opts
      scoped                  scoped
      search-resids*          search-resids*
      search-resids*-cached   search-resids*-cached
      search-resids           search-resids]

  (defn tr
    "Next gen Taoensso (tr)anslation API:

    (tr
      ;; Opts map to control behaviour:
      {:dict
       {:default-locale :en
        :dict ; Resource dictionary
        {:en {:missing \"Missing translation\"
              :example {:greet \"Hello %1\"
                        :farewell \"Goodbye %1, it was nice to meet you!\"}}}}}

      ;; Descending-preference locales to try:
      [:fr-FR :en-GB-variation1]

      ;; Descending-preference dictionary resorces to try. May contain a
      ;; final non-keyword fallback:
      [:example/how-are-you? \"How are you, %1?\"]

      ;; Optional arbitrary args for insertion into compiled resource:
      [\"Steve\"])

    => \"How are you, Steve?\"


    Common opts (see `tempura/default-tr-opts` for default vals):

      :default-locale      ; Optional fallback locale to try when given
                           ; locales don't have the requested resource/s.

      :dict                ; Dictionary map of resources,
                           ; {<locale> {<k1> ... {<kn> <resource>}}}.
                           ; See also `tempura/example-dictionary`.

      :resource-compiler   ; (fn [resource]) -> [(fn [vargs]) -> <compiled-resource>].
                           ; Useful if you want to customize any part of how
                           ; dictionary resources are compiled.

      :missing-resource-fn ; (fn [{:keys [opts locales resource-ids resource-args]}]).
                           ; Called when requested resource/s cannot be
                           ; found. Useful for logging, etc. May return a
                           ; non-nil fallback resource value.

      :cache-dict?         ; Only reason you'd want this off is if
                           ; you're using :__load-resource imports and
                           ; and want dictionary to pick up changes.

      :cache-locales?      ; Client will usu. be dealing with a small
                           ; number of locales, the server often a
                           ; large number in the general case. `tr`
                           ; partials may want to enable cached locale
                           ; expansion (e.g. in the context of a
                           ; particular user's Ring request, etc.).

      :cache-resources?    ; For the very highest possible performance
                           ; when using a limited domain of locales +
                           ; resource ids."

    ([opts locales resource-ids] (tr opts locales resource-ids nil))
    ([opts locales resource-ids resource-args]

     (have? vector? resource-ids)
     ;; (have? [:or nil? vector? map?] resource-args)

     (when (seq resource-ids)
       (let [opts (merge-into-default-opts opts *tr-opts*)
             {:keys [default-locale dict scope-fn
                     cache-dict?      #_cache-dict-compilation?
                     cache-locales?   #_cache-locale-expansion?
                     cache-resources? #_cache-resource-id-searches?]}
             opts

             locales       (if (nil? locales) [] (have vector? locales))
             dict          (impl/compile-dictionary cache-dict? dict)
             locale-splits (impl/expand-locales cache-locales?
                             (enc/conj-some locales default-locale))

             ?fb-resource  (let [last-res (peek resource-ids)]
                             (when-not (keyword? last-res) last-res))
             resource-ids (if ?fb-resource (pop resource-ids) resource-ids)

             ;; For root scopes, disabling scope, other *vars*, etc.
             resid-scope (when-some [f scope-fn] (f))

             ?matching-resource
             (or
               (when (seq resource-ids) ; *Any* non-fb resource ids?
                 (search-resids cache-resources?
                   dict locale-splits resid-scope resource-ids))

               ?fb-resource

               ;; No scope from here:

               (when-let [mrf (get :missing-resource-fn opts)]
                 (mrf ; Nb can return nnil to use result as resource
                   {:opts opts :locales locales :resource-ids resource-ids
                    :resource-args resource-args}))

               (search-resids cache-resources?
                 dict locale-splits nil [:missing]))]

         (when-let [r ?matching-resource]
           (let [resource-compiler (get opts :resource-compiler)
                 vargs (if-some [args resource-args] (impl/vargs args) [])]

             ;; Could also supply matching resid to compiler, but think it'd
             ;; be better to keep ids single-purpose. Any meta compiler
             ;; options, notes, etc. should be provided with res content.
             ((resource-compiler r) vargs))))))))

(comment
  (tr {} [:en] [:resid1 "Hello there"])   ; => text
  (tr {} [:en] [:resid1 ["Hello world"]]) ; => vec (Hiccup, etc.)
  (tr {} [:en] [:resid2 ["Hello **world**"]])
  (tr {} [:en] [:resid3 ["Hello " [:br] [:strong "world"]]])

  (def c1
    {:cache-dict?    false
     :cache-locales? false
     :default-local :en
     :dict example-dictionary})

  (tr {} [:en] [:foo :bar [:span "This is a **test**"]])
  (tr c1 [:en-GB] [:example/greet] ["Steve"])
  (tr c1 [:en] [:example/bar-copy "Fallback"])
  (tr c1 [:en] [:foo :bar])
  (with-tr-scope :example (tr c1 [:en] [:foo]))

  (qb 1000
    (tr c1 [:en] ["Hi %1"]        ["Steve"])
    (tr c1 [:en] ["Hi %1"]        {1 "Steve"})
    (tr c1 [:en] [ "Hi **%1**!"]  ["Steve"])
    (tr c1 [:en] [["Hi **%1**!"]] ["Steve"])
    (tr c1 [:en-US] [:foo :bar ["Hi **%1**!"]] ["Steve"])
    (tr c1 [:DE-US] [:foo :bar] ["Steve"])))

;;;; Ring middleware

#?(:clj
   (defn parse-http-accept-header
     "Parses given HTTP Accept header string and returns ordered vector
     of choices. No auto normalization. Ref. https://goo.gl/c4ClkR."
     [header]
     (when header
       (when-let [csvs (not-empty (str/split header #","))]
         (let [idx_ (volatile! -1)
               m-sort-by
               (reduce
                 (fn [acc in]
                   (let [in (str/trim in)]
                     (if (or (= in "") (get acc in))
                       acc
                       (let [[choice q] (str/split in #";")
                             choice (str/trim choice)
                             q (or (when q
                                     (enc/as-?float
                                       (get (str/split q #"=") 1)))
                                 1.0)
                             sort-by [(- q) (vswap! idx_ inc)]]
                         (assoc acc [choice q] sort-by)))))
                 {}
                 csvs)]

           (into [] (map (fn [[choice]] choice))
             (sort-by m-sort-by (keys m-sort-by))))))))

(comment
  (mapv parse-http-accept-header
    [nil "en-GB" "da, en-gb;q=0.8, en;q=0.7" "en-GB,en;q=0.8,en-US;q=0.6"
     "en-GB  ,  en; q=0.8, en-US;  q=0.6" "a," "es-ES, en-US"]))

#?(:clj
   (defn wrap-ring-request
     "Alpha, subject to change.
     Wraps Ring handler to add the following keys to requests:
       :accept-langs ; e.g. [\"en-ES\" \"en-US\"], parsed from request's
                     ; Accept-Language HTTP header.

       :tr           ; (partial tr tr-opts (:tr-locales ring-req accept-langs)),
                     ; (fn ([resource-ids]) ([resource-ids args]))"

     [handler {:keys [tr-opts]}]
     (fn [ring-req]
       (let [accept-langs
             (when-let [h (get-in ring-req [:headers "accept-language"])]
               (parse-http-accept-header h))

             tr-opts (enc/assoc-nx tr-opts :cache-locales? true)
             tr      (partial tr tr-opts (:tr-locales ring-req accept-langs))

             ring-req
             (assoc ring-req
               #_:tempura/accept-langs :accept-langs accept-langs
               #_:tempura/tr :tr tr)]

         (handler ring-req)))))
