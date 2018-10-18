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

## Tutorial

Add the necessary dependency to your project:
```clojure
[com.taoensso/tempura "1.2.1"]
```
The following walk-through assumes that you are using a REPL and have
required tempura as follows:
```clojure
(def my-clj-or-cljs-ns
  (:require [taoensso.tempura :as tempura :refer [tr]]))
```

### Getting started

It's best practice to define a Clojure map for localizable
resources. At the top-level the keys refer to the language and the
values are further maps with translation keys and the corresponding
localizations in the given language -- the following example offers a
map with localizations in English, German and Chinese:
```clojure
(def translations
  {; English language resources
   :en {:missing       "**MISSING**" ; Fallback for missing resources
        :hello-world   "Hello, world!"
        :hello-tempura "Hello tempura!"}

   ; German language resources
   :de {:missing "**FEHLT**"
        :hello-world "Hallo Welt!"
        :hello-tempura "Hallo tempura!"}
   ; Chinese language resources
   :zh {:missing "**失踪**"
        :hello-world "世界，你好"}})
```
Using the translations is straightforward -- using the function call
`tr` the correct localization is given via
```clojure
(tr {:dict translations} [:en] [:hello-world])
```
which returns the English translation `Hello, world!`. The first
parameter of `tr` contains the options (where the `:dict` is mandatory
to supply the map with translations), the second is a vector of
languages and the third is a vector with the translation key.

Likewise, the Chinese translation is recovered via
```clojure
(tr {:dict translations} [:zh] [:hello-world])
```
which correctly returns `世界，你好`.

### Handle missing keys

In the above example the key `:hello-tempura` was missing from the
Chinese map. Missing resources (and also misspelled resource keys) are
the reason why both the languages as well as the translation keys are
vectors and not merely simple elements. If a resource is missing then
first the vector of supplied languages is searched (until the resource
is found in a different language) and then the vector of translation
keys is searched. The former allows to display strings in a "fallback"
language like English, the latter allows to mark resources as missing
by using the `:missing` key as a marker.

Thus, the call
```clojure
(tr {:dict translations} [lang :en] [res-key :missing])
```
will
1. search for the key `res-key` in language `lang` in the
   `translations` map.
2. If it fails to find one, it will look up the key in the `:en`
   English language.
3. If it still fails to find that one, it displays the value of
   `:missing` in the `lang` language map.

It is a best practice to use a convenience function that encapsulates
this default behavior, e.g.
```clojure
(defn app-tr
  "Get a localized resource.

  @param resource Resource keyword.
  @param params   Optional positional parameters.

  @return translation of `resource` in active user language or a placeholder."
  [resource & params]
  (let [lang :zh] ; Retrieve user language from database or other source
    (tr {:dict translations} [lang :en] [resource] (vec params))))
```
Then the function `app-tr` returns the localization with the above
fallback behavior in case it could not be translated properly:
```clojure
(app-tr :hello-world)   ; => "世界，你好"
(app-tr :hello-tempura) ; => "Hello tempura!"
(app-tr :haha)          ; => "**失踪**"
```

### Advanced scenarios

The above example covered the most important basic functionality. The
following are more advanced use cases. In particular, the
functionality covered is:
* using [Reagent](https://reagent-project.github.io)/Hiccup-style vectors,
* using Java-style positional parameters,
* deeper-level nesting of maps,
* escaping special HTML entities,
* custom functions for translations,
* aliasing subtrees,
* loading EDN content from disk or other external sources, and
* using plain text instead of keywords.

The following map illustrates these advanced scenarios:
```clojure
(def translations
  {; British English
   :en-GB {:missing "**EN-GB/MISSING**"

           ; Example of a Hiccup-form (e.g., for Clojurescript/Reagent)
           :faq-link [:span "Got lost? See our " [:a {:href "/faq/index.html"} "FAQ"]]

           ; Example of a Hiccup-form with Markdown
           :markdown-text [:span "This is **bold** text"]

           ; Alternative form of previous example (`[x]` is short-hand for `[:span x]`)
           :markdown-text-alt ["This is **bold** text"]

           ; You can use Java-style positional parameters
           :greet-user "Good morning, %1. You're looking %2 today."

           ; You can nest ids if you like
           :mood {:bad {:terrible "terrible"
                        :horrible "horrible"}
                  :good {:well "well"}}

           ; HTML entities can be escaped by prefixing the % with a back-tick
           :mail-support "mailto:support@tempura.com?subject=Help`%20with`%20tempura"

           ; A translation can also be a custom function
           :little-ducks (fn [[count]]
                           (let [count-word (if (< count 6)
                                              (nth ["No" "One" "Two" "Three" "Four" "Five"] count)
                                              (str count))]
                             (str count-word " little ducks")))}

  ; Regular English
  :en {:missing "**EN/MISSING**"

       ; Copy an entire subtree
       :mood-copy :en-GB/mood

       ; Import a resource as EDN content from idisk (it MUST actually exist!)
       ; :imported {:__load-resource "resources/i18n.clj"}
       }})
```
The following are usage examples of the functionality illustrated
above:
```clojure
(def en-gb-tr (partial tr {:dict translations} [:en-gb]))

(en-gb-tr [:haha])          ; => "**EN-GB/MISSING**"

(en-gb-tr [:faq-link])      ; => [:span "Got lost? See our " [:a {:href "/faq/index.html"} "FAQ"]]
(en-gb-tr [:markdown-text]) ; => [:span "This is " [:strong "bold"] " text"]
(en-gb-tr [:markdown-text-alt]) ; [:span "This is " [:strong "bold"] " text"]

(en-gb-tr [:mood.bad/horrible]) ; => "horrible"
(en-gb-tr [:greet-user] ["Dave" (en-gb-tr [:mood.good/well])]) ; => "Good morning, Dave. You're looking well today."

(en-gb-tr [:mail-support])  ; => "mailto:support@tempura.com?subject=Help%20with%20tempura"

(en-gb-tr [:little-ducks] [0]) ; => "No little ducks"
(en-gb-tr [:little-ducks] [4]) ; => "Four little ducks"
(en-gb-tr [:little-ducks] [6]) ; => "6 little ducks"

(tr {:dict translations} [:en] [:mood-copy.bad/terrible]) ; => "terrible"
```
If the translation key is not a keyword but a string it is simply
returned verbatim, i.e.,
```clojure
(en-gb-tr ["Work in progress"]) ; => "Work in progress"
```
For further options extensive inline documentation is available via
```clojure
(doc tr)
```

### Handling of Hiccup-syntax

Note that you CAN use positional parameters in a Hiccup-vector. But you CANNOT use them
in a map, i.e.,
```clojure
(def translations
  {:en {:link-tag [:a {:href "%2"} "%1"]}})
```
will replace the first positional parameter `%1`, but NOT the second at `%2`. If you
need to use positional parameter at that point you must use a function, e.g.,
```clojure
(def translations
  {:en {:link-faq (fn [[link]]
                    ["Please see the " [:a {:href link} "FAQs"]])}
   :de {:link-faq (fn [[link]]
                    ["Schauen Sie sich die " [:a {:href link} "häufigen Fragen"] " an"])}})
```
This will return the correctly localized Reagent components with the link properly injected:
```clojure
(tr {:dict translations} [:en] [:link-faq] ["https://tempura.com/faq"])
  ; => ["Please see the " [:a {:href "https://tempura.com/faq"} "FAQs"]]
(tr {:dict translations} [:de] [:link-faq] ["https://tempura.com/faq"])
  ; => ["Schauen Sie sich die " [:a {:href "https://tempura.com/faq"} "häufigen Fragen"] " an"]
```

### Running tests

Tests are supplied inline with the main source code. Test cases in
Clojure are run via
```bash
lein test
```

### Pattern: Adding translations in stages

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
