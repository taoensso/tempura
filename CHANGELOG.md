This project uses Break Versioning (https://www.taoensso.com/break-versioning)

## v1.5.3 - 2022 Oct 27

```clojure
[com.taoensso/tempura "1.5.3"]
```

> This is a major maintenance release that should be non-breaking for most users.

* **New**: Wiki docs at https://github.com/taoensso/tempura/wiki
* **New**: Add `new-tr-fn` for creating `tr` partials with fn-local cache.
* **BREAKING**: `wrap-ring-request` (alpha) API changes, see commit ed688af18de2 for details.


## v1.4.0 - 2022 Oct 17

```clojure
[com.taoensso/tempura "1.4.0"]
```

> This is a minor maintenance release that should be non-breaking.

* **New**: [Experimental] Add `:experimental/compact-vectors?` option to `get-default-resource-compiler`
* **New**: [Experimental] Add `:default-tag` option to `default-resource-compiler`
* Update dependencies


## v1.3.0 - 2022 May 12

```clojure
[com.taoensso/tempura "1.3.0"]
```

> This is a minor maintenance release that should be non-breaking for the vast majority of users.

* **BREAKING**: Now requires Clojure 1.7+
* **BREAKING**: Markdown output no longer interprets ~~ as strikeout. (That syntax is now reserved).

* [#26] **New**: Make `get-default-resource-compiler` public (@mt0erfztxt)
* **Impl**: Some minor performance improvements


## v1.2.1 - 2018 Apr 15

```clojure
[com.taoensso/tempura "1.2.1"]
```

> This is a non-breaking hotfix release.

* [#21] **Fix**: edge-case preventing blank `:missing` vals (@kaosko)

## v1.2.0 - 2018 Mar 11

```clojure
[com.taoensso/tempura "1.2.0"]
```

> This is a non-breaking maintenance release.

* [#20] Fix: bug with arg-only resources (@DjebbZ)
* **Impl**: Bump dependencies, incl. ClojureScript

## v1.1.2 - 2017 Mar 28

```clojure
[com.taoensso/tempura "1.1.2"]
```

> This is a non-breaking hotfix release.

* [#9] **Fix**: Typo was breaking :missing-resource-fn (@brjann)

## v1.1.1 - 2017 Feb 18

```clojure
[com.taoensso/tempura "1.1.1"]
```

* **Fix**: broken unit tests

## v1.1.0 - 2017 Feb 16

```clojure
[com.taoensso/tempura "1.1.0"]
```

* **BREAKING**: Throw on missing `:__load-resource` files (used to fail silently)
* [#5] **New**: Added experimental utils: `load-resource-at-runtime`, `load-resource-at-compile-time`
* **Impl**: Normalize locale casing: `:en-GB` <=> `:en-gb`

## v1.0.0 - 2016 Dec 17

```clojure
[com.taoensso/tempura "1.0.0"]
```

> Non-breaking v1.0.0 release.

* **Fix**: [#4] `tr` doc-string typo

## v1.0.0-RC4 - 2016 Nov 13

```clojure
[com.taoensso/tempura "1.0.0-RC4"]
```

* **BREAKING**: Ring middleware: use namespaced keys.
* [#3] **New**: Make `parse-http-accept-header` public (@yogthos).


## v1.0.0-RC3 - 2016 Oct 17

```clojure
[com.taoensso/tempura "1.0.0-RC3"]
```

> Initial public release. Sorry for taking so long to finally document+publish this!
