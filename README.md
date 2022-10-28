<a href="https://www.taoensso.com/clojure" title="More stuff by @ptaoussanis at www.taoensso.com"><img src="https://www.taoensso.com/open-source.png" alt="Taoensso open source" width="340"/></a>  
[**API**][cljdoc docs] | [**Wiki**][GitHub wiki] | [Latest releases](#latest-releases) | [Slack channel][]

# Tempura

### Simple text localization library for Clojure/Script

**Tempura** is mature, developer-friendly library for supporting **multilingual text** in your Clojure and ClojureScript applications.

It offers a simple, easy-to-use API that allows you to **expand localization content over time**, without bogging down early development.

## Latest release/s

- `2022-10-27` `v1.5.3`: [release info](../../releases/tag/v1.5.3)

[![Main tests][Main tests SVG]][Main tests URL]
[![Graal tests][Graal tests SVG]][Graal tests URL]

See [here][GitHub releases] for earlier releases.

## Why Tempura?

 - Tiny (**single fn**), **cross-platform all-Clojure API** for providing multilingual text content.
 - Matches [gettext](https://en.wikipedia.org/wiki/Gettext)'s convenience for **embedding default content** directly in code (optional).
 - Exceeds [gettext](https://en.wikipedia.org/wiki/Gettext)'s ability to handle **versioned content** through unique content ids.
 - Works out-the-box with plain text, Hiccup, **Reactjs**, etc.
 - Easy, optional platform-appropriate support for simple **Markdown styles**.
 - **Flexibility**: completely open/pluggable resource compiler.
 - **Performance**: match or exceed `format` performance through compilation + smart caching.
 - All-Clojure **(edn) dictionary format** for ease of use, easy compile-**and-runtime** manipulation, etc.
 - Focused on common-case **translation** and no other aspects of i18n/L10n.

## 10-second example

```clojure
(require '[taoensso.tempura :as tempura :refer [tr]]))

(tr ; For "translate"
    {:dict ; Dictionary of translations
     {:sw {:missing "sw/?" :r1 "sw/r1" :r2 "sw/r2"}
      :en {:missing "en/?" :r1 "en/r1" :r2 "en/r2"}}}

    [:sw :en <...>] ; Locales   (desc priority)
    [:r1 :r2 <...>  ; Resources (desc priority)
     <?fallback-str> ; Optional final fallback string
     ])

;; =>

(or
  sw/r1 sw/r2  <...> ; Descending-priority resources in priority-1 locale
  en/r1 en/r2  <...> ; ''                            in priority-2 locale
  <...>

  ?fallback-str ; Optional fallback string (as last element in resources vec)

  sw/? ; Missing (error) resource in priority-1 locale
  en/? ; ''                          priority-2 locale

  nil  ; If none of the above exist
  )

;; etc.

;; Note that ?fallback-str is super handy for development before you
;; have translations ready, e.g.:

(tr {:dict {}} [:en] [:sign-in-btn "Sign in here!"])
;; => "Sign in here!"

;; Tempura also supports Hiccup with Markdown-like styles, e.g.:

(tr {:dict {}} [:en] [:sign-in-btn ["**Sign in** here!"]])
;; => [:span [:strong "Sign in"] " here!"]

```

## Documentation

- [Wiki][GitHub wiki] (getting started, usage, etc.)
- API reference: [cljdoc][cljdoc docs], [Codox][Codox docs]
- Support: [Slack channel][] or [GitHub issues][]

## Funding

You can [help support][sponsor] continued work on this project, thank you!! 🙏

## License

Copyright &copy; 2016-2024 [Peter Taoussanis][].  
Licensed under [EPL 1.0](LICENSE.txt) (same as Clojure).

<!-- Common -->

[GitHub releases]: ../../releases
[GitHub issues]:   ../../issues
[GitHub wiki]:     ../../wiki
[Slack channel]: https://www.taoensso.com/slack

[Peter Taoussanis]: https://www.taoensso.com
[sponsor]:          https://www.taoensso.com/sponsor

<!-- Project -->

[Codox docs]:   https://taoensso.github.io/tempura/
[cljdoc docs]: https://cljdoc.org/d/com.taoensso/tempura/

[Clojars SVG]: https://img.shields.io/clojars/v/com.taoensso/tempura.svg
[Clojars URL]: https://clojars.org/com.taoensso/tempura

[Main tests SVG]:  https://github.com/taoensso/tempura/actions/workflows/main-tests.yml/badge.svg
[Main tests URL]:  https://github.com/taoensso/tempura/actions/workflows/main-tests.yml
[Graal tests SVG]: https://github.com/taoensso/tempura/actions/workflows/graal-tests.yml/badge.svg
[Graal tests URL]: https://github.com/taoensso/tempura/actions/workflows/graal-tests.yml