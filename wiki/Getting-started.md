# Setup

Add the [relevant dependency](../#latest-releases) to your project:

```clojure
Leiningen: [com.taoensso/tempura               "x-y-z"] ; or
deps.edn:   com.taoensso/tempura {:mvn/version "x-y-z"}
```

And setup your namespace imports:

```clojure
(def my-app
  (:require [taoensso.tempura :as tempura :refer [tr]]))
```

# Usage

## Basics

First **define a dictionary** for translation resources:

```clojure
(def my-tempura-dictionary
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
```

And we're ready to go:

```clojure
(tr ; Just a functional call
  {:dict my-tempura-dictionary} ; Opts map, see docstring for details
  [:en-GB :fr] ; Vector of descending-preference locales to search
  [:example/foo] ; Vector of descending-preference resource-ids to search
  ) ; => "foo"

(def opts {:dict my-tempura-dictionary})
(def tr (partial tr opts [:en])) ; You'll typically use a partial like this

;; Grab a resource
(tr [:example/foo]) ; => "foo"

;; Missing resource
(tr [:example/invalid])                       ; => ":en missing text"
(tr [:example/invalid "inline-fallback"])     ; => "inline-fallback"
(tr [:example/invalid :bar "final-fallback"]) ; => "bar"

;; Let's try some argument interpolation
(tr [:example/greet] ["Steve"]) ; => "Hello Steve"

;; With inline fallback
(tr [:example/invalid "Hi %1"] ["Steve"]) ; => "Hi Steve"

;; Example of a deeply-nested resource id
(tr [:example.buttons/login-button "Login!"]) ; => "Login!"

;; Let's get a Hiccup form for Reactjs, etc.
;; Note how the Markdown gets expanded into appropriate Hiccup forms:
(tr [:example/baz]) ; => [:div "This is a " [:strong "Hiccup"] " form"]

;; With inline fallback
(tr [:example/invalid [:div "My **fallback** div"]]) ; => [:div "My " [:strong "fallback"] " div"]
```

And that's it, you know the [API](https://cljdoc.org/d/com.taoensso/tempura/CURRENT/api/taoensso.tempura):

```clojure
(tr [opts locales resource-ids])               ; Without argument interpolation, or
(tr [opts locales resource-ids resource-args]) ; With    argument interpolation
```

See [`tr`](https://cljdoc.org/d/com.taoensso/tempura/CURRENT/api/taoensso.tempura#tr) for more info on available opts, etc.

## Adding translations over time

The support for `gettext`-like **inline fallback content** makes it really easy to write your application in stages, **without** translations becoming a burden until if/when you need them.

Assuming we have a `tr` partial `(tr [resource-ids] [resource-ids resource-args])`:

```clojure
"Please login here"        ; Phase 1: no locale support (avoid)
(tr ["Please login here"]) ; Phase 2: works just like a text literal during dev

;; Phase 3: now supports translations when provided under the `:please-login`
;; resource id, otherwise falls back to the (English) text literal:
(tr [:please-login "Please login here"])
```

This means:

 * You can write dev/prototype apps w/o worrying about translations or naming resource ids.
 * Once your app design settles down, you can add resource ids.
 * Your translation team can now populate locale dictionaries at their own pace.
 * You can keep the default inline content as context for your developers.

I'll note that since the API is so pleasant, it's actually often much _less_ effort for your developers to use `tr` than it would be for them to write the equivalent Hiccup structures by hand, etc.:

```clojure
;; Compare the following two equivalent values:
(tr [["Hi %1, please enter your **login details** below:"]] [user-name])
[:span "Hi " user-name ", please enter your " [:strong "login details"] " below:"]
```

> Note that `["foo"]` is an optional resource content shorthand for the common-case `[:span "foo"]`

If it's easy to use, it'll be easy to get your developers in the habit of writing content this way - which means that there's a trivial path to adding multilingual support whenever it makes sense to do so.

## Use with Ring

See [`tempura/wrap-ring-request`](https://cljdoc.org/d/com.taoensso/tempura/CURRENT/api/taoensso.tempura#wrap-ring-request).

## Use with [Reagent](https://github.com/reagent-project/reagent), etc.

Tempura was specifically designed to work with Reactjs applications, and works great with Reagent out-the-box.

 * Step 1: Setup your ns imports so that your client has access to [`tempura/tr`](https://cljdoc.org/d/com.taoensso/tempura/CURRENT/api/taoensso.tempura#tr).
 * Step 2: Make sure your client has an appropriate dictionary [1].
 * Step 3: Call `tr` with the appropriate dictionary.

Couldn't be simpler.

**[1]** If your dictionaries are small, you could just define them with the rest of your client code. Or you can define them on the server-side and clients can fetch the relevant part/s through an Ajax request, etc. Remember that Tempura dictionaries are just plain Clojure maps, so they're trivially easy to modify/filter.

## Use with [XLIFF](https://en.wikipedia.org/wiki/XLIFF), etc.

Using Tempura with other industry-standard tools shouldn't be hard to do, you'll just need a conversion tool to/from edn. Haven't had a need for this myself, but PRs welcome!

# Resource search behaviour

Search behaviour can be initially non-obvious, though it's hopefully quite logical and useful once clarified.

Given the following Tempura call:

```clojure
(tr
  {:default-locale :de ; Defaults to :en if unspecified (may also be nil to disable)
   :dict
   {:en {:r1 "en/r1" :r2 "en/r2" :missing "en/?"}
    :sw {:r1 "sw/r1" :r2 "sw/r2" :missing "sw/?"}
    :de {:r1 "de/r1" :r2 "de/r2" :missing "de/?"}}}

  [:sw :en] ; Locales   to search in
  [:r1 :r2] ; Resources to search for
  )
```

The resulting search order will be:

```clojure
(or
  sw/r1 sw/r2 ; 1st-priority locale, with desc-priority resources
  en/r1 en/r2 ; 2nd-priority locale, with desc-priority resources
  de/r1 de/r2 ; 3rd-priority locale, with desc-priority resources

  sw/?        ; 1st-priority locale, with error resource
  en/?        ; 2nd-priority locale, with error resource
  de/?        ; 3rd-priority locale, with error resource

  nil         ; Give up - nothing available, not even an error resource
  )
```

This generalizes.

Let's say you have:
  - `x` number of locales: `[:l1 :l2 ... :lx]` with `{:default-locale :ld}`
  - `y` number of resources `[:r1 :r2 ... :ry]`

This will be equivalent to:
  - `x+1` number of locales: `[:l1 :l2 ... :lx :ld]`
  - `y` number of resources: `[:r1 :r2 ... :ry]`

Then the search order will be:
```clojure
(or
  l1/r1 l1/r2 ... l1/ry ; Try all resources in :l1
  l2/r1 l2/r2 ... l2/ry ; Try all resources in :l2
  ...
  lx/r1 lx/r2 ... lx/ry ; Try all resources in :ln
  ld/r1 ld/r2 ... ld/rn ; Try all resources in :default-locale

  ;; No valid resources, now try to find an error message:

  l1/?
  l2/?
  l3/?
  ...
  lx/?
  ld/?

  nil ; Give up - nothing available, not even an error resource
)
```

## String resources

An important note: "resources" in the above examples can be:

 - Keys in a map dictionary, **OR**
 - **Strings**

e.g. `[:r1 :r2 "r3 string"]`

String resources can be super useful when writing early code, before translations are available (indeed possibly before you even know for sure what canonical text you want to have translated).

Let's say you're writing a button for the first time. The provisional button text could maybe be something like "Sign-in", but this isn't finalised yet even for your canonical language.

You can use something like:

`(tr <opts> <locales> [:sign-in-button "Sign in"])` as a quick way to get started.

This way you have something readable in the UI from day 1, and there's no temptation to just bypass the translation system altogether.

As a tradeoff, note though that the presence of string translations like this will prevent any `:missing` errors from being displayed.

# Performance

There's two aspects of Tempura performance worth measuring:

- Resource **lookup**, and
- Resource **compilation**

Both are highly optimized, and intelligently cached. In fact, caching is quite easy since most applications have a small number of unique multilingual text assets. Assets are compiled each time they're encountered for the first time, and the compilation cached.

As an example:

```clojure
`(tr [["Hi %1, please enter your **login details** below:"]] [user-name])`

;; Will compile the inner resource to an optimized function like this:

(fn [user-name] [:span "Hi " user-name ", please enter your " [:strong "login details"] " below:"])
```

So performance is often on par with the best possible hand-optimized monolingual code.