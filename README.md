<a href="https://www.taoensso.com" title="More stuff by @ptaoussanis at www.taoensso.com">
<img src="https://www.taoensso.com/taoensso-open-source.png" alt="Taoensso open-source" width="400"/></a>

**[CHANGELOG]** | [API] | current [Break Version]:

```clojure
[com.taoensso/tempura "1.2.1"]
```

> Please consider helping to [support my continued open-source Clojure/Script work]? 
> 
> Even small contributions can add up + make a big difference to help sustain my time writing, maintaining, and supporting Tempura and other Clojure/Script libraries. **Thank you!**
>
> \- Peter Taoussanis

# Tempura

### Pure Clojure/Script i18n translations library

## Objectives

 * Tiny (**single fn**), **cross-platform all-Clojure API** for providing multilingual content.
 * Match [gettext]'s convenience for **embedding default content** directly in code (optional).
 * Exceed `gettext`'s ability to handle **versioned content** through unique content ids.
 * Work out-the-box with plain text, Hiccup, **Reactjs**, ...
 * Easy, optional platform-appropriate support for simple **Markdown styles**.
 * **Flexibility**: completely open/pluggable resource compiler.
 * **Performance**: match or exceed `format` performance through compilation + smart caching.
 * All-Clojure **(edn) dictionary format** for ease of use, easy compile-**and-runtime** manipulation, etc.
 * Focus only on common-case **translation** and no other aspects of i18n/L10n.

## Quickstart

Add the necessary dependency to your project:

```clojure
[com.taoensso/tempura "1.2.1"]
```

Setup your namespace imports:

```clojure
(def my-clj-or-cljs-ns
  (:require [taoensso.tempura :as tempura :refer [tr]]))
```

Define a dictionary for translation resources:

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

And that's it, you know the [API]:

```clojure
(tr [opts locales resource-ids])               ; Without argument interpolation, or
(tr [opts locales resource-ids resource-args]) ; With    argument interpolation
```

Please see the `tr` docstring for more info on available opts, etc.

## Pattern: adding translations in stages

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

## FAQ

#### How's the performance? These seem like expensive transformations.

There's two aspects of performance worth measuring: resource **lookup**, and resource **compilation**.

Both are highly optimized, and intelligently cached. In fact, caching is quite easy since most applications have a small number of unique multilingual text assets. Assets are compiled each time they're encountered for the first time, and the compilation cached.

As an example:

```clojure
`(tr [["Hi %1, please enter your **login details** below:"]] [user-name])`

;; Will compiled the inner resource to an optimized function like this:

(fn [user-name] [:span "Hi " user-name ", please enter your " [:strong "login details"] " below:"])
```

So performance is often on par with the best possible hand-optimized monolingual code.

#### How would you use this with [Reagent], etc.?

Tempura was specifically designed to work with Reactjs applications, and works great with Reagent out-the-box.

 * Step 1: Setup your ns imports so that your client has access to `tempura/tr`.
 * Step 2: Make sure your client has an appropriate dictionary [1].
 * Step 3: Call `tr` with the appropriate dictionary.

Couldn't be simpler.

**[1]** If your dictionaries are small, you could just define them with the rest of your client code. Or you can define them on the server-side and clients can fetch the relevant part/s through an Ajax request, etc. Remember that Tempura dictionaries are just plain Clojure maps, so they're trivially easy to modify/filter.

#### Ring middleware?

Please see `tempura/wrap-ring-request`.

#### Use with XLIFF or other industry standard tools?

Shouldn't be hard to do, you'll just need a conversion tool to/from edn. Haven't had a need for this myself, but PRs welcome.

## Contacting me / contributions

Please use the project's [GitHub issues page] for all questions, ideas, etc. **Pull requests welcome**. See the project's [GitHub contributors page] for a list of contributors.

Otherwise, you can reach me at [Taoensso.com]. Happy hacking!

\- [Peter Taoussanis]

## License

Distributed under the [EPL v1.0] \(same as Clojure).  
Copyright &copy; 2016 [Peter Taoussanis].

<!--- Standard links -->
[Taoensso.com]: https://www.taoensso.com
[Peter Taoussanis]: https://www.taoensso.com
[@ptaoussanis]: https://www.taoensso.com
[More by @ptaoussanis]: https://www.taoensso.com
[Break Version]: https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md
[support my continued open-source Clojure/Script work]: http://taoensso.com/clojure/backers

<!--- Standard links (repo specific) -->
[CHANGELOG]: https://github.com/ptaoussanis/tempura/releases
[API]: http://ptaoussanis.github.io/tempura/
[GitHub issues page]: https://github.com/ptaoussanis/tempura/issues
[GitHub contributors page]: https://github.com/ptaoussanis/tempura/graphs/contributors
[EPL v1.0]: https://raw.githubusercontent.com/ptaoussanis/tempura/master/LICENSE
[Hero]: https://raw.githubusercontent.com/ptaoussanis/tempura/master/hero.png "Title"

<!--- Unique links -->
[gettext]: https://en.wikipedia.org/wiki/Gettext
[Reagent]: https://github.com/reagent-project/reagent
