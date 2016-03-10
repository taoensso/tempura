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

(def ^:dynamic *tr-scope* nil)
(def ^:dynamic *tr-opts*  nil)

(defmacro with-tr-scope [scope & body] `(binding [*tr-scope* ~scope] ~@body))
(defmacro with-tr-opts  [opts  & body] `(binding [*tr-opts*  ~opts]  ~@body))

;;;;

(def ^:private get-default-resource-compiler
  "Good general-purpose resource compiler.
  Supports output of text, and Hiccup forms with simple Markdown styles."
  (enc/memoize_
    (fn [{:keys [escape-html?]}]
      (let [esc1 (if escape-html? impl/escape-html             identity)
            esc2 (if escape-html? impl/vec-escape-html-in-strs identity)]

        (enc/memoize_
          (fn [res]
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

   :cache-dict?      #?(:clj false :cljs true)
   :cache-locales?   #?(:clj false :cljs true)
   :cache-resources? false

   :resource-compiler (get-default-resource-compiler {:escape-html? false})
   :missing-resource-fn nil ; Nb return nnil to use as resource
   #_(fn [{:keys [opts locales resource-ids vargs]}]
       (debugf "Missing tr resource: %s" [locales resource-ids])
       nil)})

(def example-dictionary
  {:en-GB {:missing ":en-GB missing text"
           :example {:greet "Good day %1!"}}

   :en {:missing ":en missing text"
        :example {:greet "Hello %1"
                  :farewell "Goodbye %1"
                  :foo "foo"
                  :bar "bar"
                  :bar-copy :en.example/bar}
        :example-copy :en/example
        :import {:__load-resource "slurps/i18n.clj"}}})

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
                (reduced (reduced (reduced res)))))
            acc locale-split))
        acc resids))
    nil locale-splits))

(def ^:private search-resids*-cached (enc/memoize_ search-resids*))

(defn- search-resids [cache? dict locale-splits ?scope resids]
  (if cache?
    (search-resids*-cached dict locale-splits ?scope resids)
    (search-resids*        dict locale-splits ?scope resids)))

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

      :resource-compiler   ; (fn [resource]) -> [(fn [args]) -> <compiled-resource>].
                           ; Useful if you want to customize any part of how
                           ; dictionary resources are compiled.

      :missing-resource-fn ; (fn [{:keys [opts locales resource-ids vargs]}])
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
    ([opts locales resource-ids args]

     (have? vector? resource-ids)

     (when (seq resource-ids)
       (let [opts (merge-into-default-opts opts *tr-opts*)
             {:keys [default-locale dict scope-fn
                     cache-dict?      #_cache-dict-compilation?
                     cache-locales?   #_cache-locale-expansion?
                     cache-resources? #_cache-resource-id-searches?]}
             opts

             locales       (have vector? (if (nil? locales) [] locales))
             dict          (impl/compile-dictionary cache-dict? dict)
             locale-splits (impl/expand-locales cache-locales?
                             (enc/conj-some locales default-locale))

             ?fb-resource  (let [last-res (peek resource-ids)]
                             (when-not (keyword? last-res) last-res))
             resource-ids (if ?fb-resource (pop resource-ids) resource-ids)
             resid-scope
             (if-let [scope-fn (get opts :scope-fn)]
               (scope-fn) ; For root scopes, other *vars*, etc.
               *tr-scope*)

             vargs_ (when args (delay (impl/->vargs args))) ; Experimental

             ?matching-resource
             (or
               (when (seq resource-ids) ; *Any* non-fb resource ids?
                 (search-resids cache-resources?
                   dict locale-splits resid-scope resource-ids))

               ?fb-resource ; Nb no scope:

               (when-let [mrf (get :missing-resource-fn opts)]
                 (mrf ; Nb can return nnil to use result as resource
                   {:opts opts :locales locales :resource-ids resource-ids
                    :vargs (force vargs_)}))

               (search-resids cache-resources?
                 dict locale-splits nil [:missing]))]

         (when-let [r ?matching-resource]
           (let [resource-compiler (get opts :resource-compiler)]
             ((resource-compiler r) (force vargs_)))))))))

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
   (defn- parse-http-accept-header
     "Parses given HTTP Accept header string and returns ordered vector
     of choices like. No auto normalization.
     Ref. https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html 14.4."
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
