(ns taoensso.tempura
  "Pure Clojure/Script i18n translations library."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require
   [clojure.string :as str]
   [taoensso.encore       :as enc  :refer [have have?]]
   [taoensso.tempura.impl :as impl :refer []]))

(enc/assert-min-encore-version [3 112 0])

(def ^:dynamic *tr-opts*  nil)
(def ^:dynamic *tr-scope* nil)

#?(:clj (defmacro with-tr-opts [opts & body] `(binding [*tr-opts* ~opts] ~@body)))
#?(:clj
   (defmacro with-tr-scope
     "`(with-tr-scope :foo.bar (tr _ _ [:baz]))` is equivalent to
     `(tr _ _ [:foo.bar/baz])`"
     [scope & body]
     `(binding [*tr-scope* ~scope] ~@body)))

;;;;

(defn- compact [xs]
  (let [[x0 & xn] xs
        out
        (loop [[x1 & xn*] xn
               acc [x0]
               sb  nil]

          (cond
            (nil?    x1) (if sb (conj acc (str sb)) acc)
            (vector? x1)
            (recur xn*
              (conj
                (if sb (conj acc (str sb)) acc)
                (compact x1)) nil)

            :else
            (recur xn* acc
              (if sb
                (enc/sb-append sb (str x1))
                (enc/str-builder  (str x1))))))]

    (if (and (== (count out) 2) (enc/identical-kw? (nth out 0) :span))
      (nth out 1)
      (do  out))))

(comment :see-tests)
(comment (enc/qb 1e4 (compact [:span "a" "b" [:strong "c" "d"] "e" "f"]))) ; 7.16

(defn get-default-resource-compiler
  "Implementation detail.
  Good general-purpose resource compiler.
  Supports output of text, and Hiccup forms with simple Markdown styles."
  [{:keys [default-tag escape-html? experimental/compact-vectors?]
    :or   {default-tag :span}}]

  (let [?esc1    (if escape-html?     impl/escape-html             identity)
        ?esc2    (if escape-html?     impl/vec-escape-html-in-strs identity)
        ?compact (if compact-vectors? (fn [f] (comp compact f))    identity)]

    (enc/fmemoize ; Ref. transparent, limited domain
      (fn [res] ; (fn [vargs]) -> <compiled-resource>
        (enc/cond! ; Nb no keywords, nils, etc.
          (fn?     res) (-> res) ; Completely arb, full control
          (string? res) (-> res ?esc1 impl/str->vargs-fn)
          (vector? res)
          (?compact ; [:span "foo" "bar"] -> "foobar", etc. (uncached)
            (-> res
              (impl/vec->vtag default-tag)
              impl/vec-explode-styles-in-strs
              impl/vec-explode-args-in-strs
              ?esc2              ; Avoid for Reactjs
              impl/vec->vargs-fn ; (fn [args]) -> result (uncached)
              )))))))

(comment :see-tests)

(def default-tr-opts
  {:default-locale :en
   :dict {:en {:missing "[Missing tr resource]"}}
   :scope-fn (fn [] *tr-scope*)

   :cache-dict?      #?(:clj false :cljs :global)
   :cache-locales?   #?(:clj false :cljs :global)
   :cache-resources?         false

   :resource-compiler (get-default-resource-compiler {:escape-html? false})
   :missing-resource-fn nil ; Nb return nnil to use as resource
   #_(fn [{:keys [opts locales resource-ids resource-args]}]
       (debugf "Missing tr resource: %s" [locales resource-ids])
       nil)})

(def ^:private merge-into-default-opts
  (enc/fmemoize
    (fn [opts dynamic-opts]
      (merge default-tr-opts opts dynamic-opts))))

;;;;

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

#?(:clj
   (defn load-resource-at-runtime
     "Experimental, subject to change.
     Reads and returns an edn resource on classpath, at runtime.
     Supported by: clj only (cljs support not possible).

     A {:my-key {:__load-resource \"my-file.edn\"}} dictionary entry is
     equivalent to {:my-key (load-resource-at-runtime \"my-file.edn\")}.

     See also `load-resource-at-compile-time`."

     [rname] (impl/load-resource rname)))

#?(:clj
   (defmacro load-resource-at-compile-time
     "Experimental, subject to change.
     Reads and inlines an edn resource on classpath, at compile-time.
     Supported by: both clj and cljs.

     See also `load-resource-at-runtime`."

     [rname] (impl/load-resource rname)))

(comment (load-resource-at-compile-time "foo.edn"))

(defn- caching [cache? f f*]
  (case cache?
    (nil false)             f
    :fn-local (enc/fmemoize f)
    f* ; Assume truthy => :global
    ))

(comment (caching true identity identity))

(let [;;; Global caches
      compile-dictionary* (enc/fmemoize impl/compile-dictionary)
      expand-locales*     (enc/fmemoize impl/expand-locales)
      search-resids*      (enc/fmemoize impl/search-resids)]

  (defn new-tr-fn
    "Returns a new translate (\"tr\") function,
      (fn tr [locales resource-ids ?resource-args]) -> translation.

    Common opts:

      :default-locale      ; Optional fallback locale to try when given locales don't
                           ; have the requested resource/s.
                           ; Default is `:en`.

      :dict                ; Dictionary map of resources,
                           ; {<locale> {<k1> ... {<kn> <resource>}}}.
                           ; See `tempura/example-dictionary` for more info.

      :resource-compiler   ; (fn [resource]) -> <(fn [vargs]) -> <compiled-resource>>.
                           ; Useful if you want to customize any part of how
                           ; dictionary resources are compiled.

      :missing-resource-fn ; (fn [{:keys [opts locales resource-ids resource-args]}]).
                           ; Called when requested resource/s cannot be found. Useful
                           ; for logging, etc. May return a non-nil fallback resource
                           ; value.

      :cache-dict?         ; Cache dictionary compilation? Improves performance,
                           ; usually safe. You probably want this enabled in
                           ; production, though you might want it disabled in
                           ; development if you use `:__load-resource` dictionary
                           ; imports and want resource changes to automatically
                           ; reflect.
                           ;
                           ; Default is `false` for Clj and `:global` for Cljs.

      :cache-locales?      ; Cache locales processing? Improves performance, safe iff
                           ; the returned `tr` fn will see a limited number of unique
                           ; `locales` arguments (common example: calling
                           ; `tempura/new-tr-fn` for each Ring request).
                           ;
                           ; Default is `false` for Clj and `:global` for Cljs.

      :cache-resources?    ; Cache resource lookup? Improves performance but will use
                           ; memory for every unique combination of `locales` and
                           ; `resource-ids`. Safe only if these are limited in number.
                           ;
                           ; Default is `false`.

    Possible values for `:cach-<x>` options:

      falsey           ; Use no cache
      `:fn-local`      ; Use a cache local to the returned `tr` fn
      `:global`/truthy ; Use a cache shared among all `tr` fns with `:global` cache

    Example:

      ;; Define a tr fn
      (def my-tr ; (fn [locales resource-ids ?resource-args]) -> translation
        (new-tr-fn
          {:dict
           {:en {:missing \"Missing translation\"
                 :example {:greet \"Hello %1\"
                           :farewell \"Goodbye %1, it was nice to meet you!\"}}}}))

      ;; Then call it
      (my-tr
        [:fr-FR :en-GB-variation1] ; Descending-preference locales to try

        ;; Descending-preference dictionary resorces to try.
        ;; May contain a final non-keyword fallback:
        [:example/how-are-you? \"How are you, %1?\"]

        ;; Optional arbitrary args for insertion into compiled resource:
        [\"Steve\"])

        => \"How are you, Steve?\"

    See `tempura/default-tr-opts` for detailed default options.
    See also `tempura/tr`.

    For further info & examples, Ref.
      https://github.com/ptaoussanis/tempura and
      https://github.com/ptaoussanis/tempura/wiki/Tempura-documentation"

    [opts]
    (let [opts (merge-into-default-opts opts *tr-opts*)
          {:keys [default-locale
                  dict
                  scope-fn
                  cache-dict?
                  cache-locales?
                  cache-resources?]} opts

          compile-dictionary (caching cache-dict?      impl/compile-dictionary compile-dictionary*)
          expand-locales     (caching cache-locales?   impl/expand-locales     expand-locales*)
          search-resids      (caching cache-resources? impl/search-resids      search-resids*)]

      (fn tr
        ([locales resource-ids              ] (tr locales resource-ids nil))
        ([locales resource-ids resource-args]

         (have? vector? resource-ids)
         ;; (have? [:or nil? vector? map?] resource-args)

         (when (seq resource-ids)
           (let [dict    (compile-dictionary dict)
                 locales (force locales)
                 locales (if (nil? locales) [] (have vector? locales))
                 locales
                 (enc/cond
                   (nil?    default-locale)       locales
                   (vector? default-locale) (into locales default-locale) ; Undocumented
                   :else                    (conj locales default-locale))

                 locale-splits (expand-locales locales)
                 ?fb-resource  (let [last-res (peek resource-ids)]
                                 (when-not (keyword? last-res) last-res))
                 resource-ids (if ?fb-resource (pop resource-ids) resource-ids)

                 ;; For root scopes, disabling scope, other *vars*, etc.
                 resid-scope (when-some [f scope-fn] (f))

                 ?matching-resource
                 (or
                   (when (seq resource-ids) ; *Any* non-fb resource ids?
                     (search-resids dict locale-splits resid-scope resource-ids))

                   ?fb-resource

                   ;; No scope from here:

                   (when-let [mrf (get opts :missing-resource-fn)]
                     (mrf ; Nb can return nnil to use result as resource
                       {:opts opts :locales locales :resource-ids resource-ids
                        :resource-args resource-args}))

                   (search-resids dict locale-splits nil [:missing]))]

             (when-let [r ?matching-resource]
               (let [resource-compiler (get opts :resource-compiler)
                     vargs (if-some [args resource-args] (impl/vargs args) [])]

                 ;; Could also supply matching resid to compiler, but think it'd
                 ;; be better to keep ids single-purpose. Any meta compiler
                 ;; options, notes, etc. should be provided with res content.
                 ((resource-compiler r) vargs))))))))))

(defn tr
  "Translate (\"tr\") function,
    (fn tr [opts locales resource-ids ?resource-args]) -> translation.

  See `tempura/new-tr-fn` for full documentation, and for fn-local caching."

  ([opts locales resource-ids              ] (tr opts locales resource-ids nil))
  ([opts locales resource-ids resource-args]
   ((new-tr-fn opts) locales resource-ids resource-args)))

(comment
  (tr {} [:en] [:resid1 "Hello there"])   ; => text
  (tr {} [:en] [:resid1 ["Hello world"]]) ; => vec (Hiccup, etc.)
  (tr {} [:en] [:resid1 ["Hello %1"]] ["Steve"])
  (tr {} [:en] [:resid2 ["Hello **world**"]])
  (tr {} [:en] [:resid3 ["Hello " [:br] [:strong "world"]]])
  (tr {} [:en] [["foo"]])

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

  (qb 1e3
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
         (let [idx_ (enc/counter)
               m-sort-by
               (reduce
                 (fn [acc in]
                   (let [in (str/trim in)]
                     (if (or (= in "") (get acc in))
                       acc
                       (let [[choice q] (str/split in #";")
                             choice (str/trim choice)
                             ^double q
                             (or (when q
                                   (enc/as-?float
                                     (get (str/split q #"=") 1)))
                               1.0)
                             sort-by [(- q) (idx_)]]
                         (assoc acc [choice q] sort-by)))))
                 {}
                 csvs)]

           (into [] (map (fn [[choice]] choice))
             (sort-by m-sort-by (keys m-sort-by))))))))

#?(:clj
   (def ^:private parse-http-accept-header*
     (enc/memoize 1000 parse-http-accept-header)))

(comment
  (enc/qb 1e4
    (mapv parse-http-accept-header
      [nil "en-GB" "da, en-gb;q=0.8, en;q=0.7" "en-GB,en;q=0.8,en-US;q=0.6"
       "en-GB  ,  en; q=0.8, en-US;  q=0.6" "a," "es-ES, en-US"]))) ; 133.9

#?(:clj
   (defn wrap-ring-request
     "Alpha, subject to change.
     Wraps Ring handler to add the following keys to Ring requests:

       :tempura/accept-langs_ ; Possible delay with value parsed from Ring
                              ; request's \"Accept-Language\" HTTP header.
                              ; E.g. value: [\"en-ES\" \"en-US\"].

       :tempura/tr ; (partial tempura/new-tr-fn tr-opts
                   ;   (or locales (:tempura/locales ring-req) accept-langs_)),
                   ; => (fn [resource-ids ?resource-args]) -> translation

     `tr-opts` will by default include {:cache-locales? :fn-local}.

     See `tempura/new-tr-fn` for full documentation on `tr-opts`, etc."

     [handler {:keys [tr-opts locales]}]

     (fn [ring-req]
       (let [accept-langs_
             (delay
               (when-let [h (get-in ring-req [:headers "accept-language"])]
                 (parse-http-accept-header* h)))

             locales_ (or locales (get ring-req :tempura/locales) accept-langs_)
             tr-opts  (enc/assoc-nx tr-opts :cache-locales? true)
             tr       (partial (new-tr-fn tr-opts) locales_)

             ring-req
             (assoc ring-req
               :tempura/accept-langs_ accept-langs_
               :tempura/tr tr)]

         (handler ring-req)))))
