(defproject ote "0.1-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0-alpha19"]
                 [org.clojure/clojurescript "1.9.908"]
                 ;; CSV parser for backend
                 [org.clojure/data.csv "0.1.4"]
                 ;; CSV parser for frontend
                 [testdouble/clojurescript.csv "0.3.0"]

                 ;; Komponenttikirjasto
                 [com.stuartsierra/component "0.3.2"]

                 ;; Logitus (clj + cljs)
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.8"]

                 ;; PostgreSQL JDBC ajuri, yhteyspooli ja muut apukirjastot
                 [org.postgresql/postgresql "42.1.4"]
                 [net.postgis/postgis-jdbc "2.1.7.2"
                  :exclusions [ch.qos.logback/logback-classic
                               ch.qos.logback/logback-core]]
                 [com.zaxxer/HikariCP "2.6.1"]
                 [org.clojure/java.jdbc "0.7.1"]
                 [webjure/jeesql "0.4.7"]
                 [specql "20180312"]

                 ;; http-kit HTTP server (and client)
                 [http-kit "2.2.0"]
                 [bk/ring-gzip "0.2.1"]
                 [ring/ring-anti-forgery "1.1.0"]
                 [clj-http "3.7.0"]

                 ;; Routing library for publishing services
                 [compojure "1.6.0"]

                 ;; Password hashing
                 [buddy/buddy-hashers "1.3.0"]

                 ;; Cache libraries
                 [org.clojure/core.cache "0.6.5"]

                 ;; Transit tietomuoto
                 [com.cognitect/transit-clj "0.8.300"]
                 [com.cognitect/transit-cljs "0.8.239"]

                 ;; Lightweight scheduler
                 [jarohen/chime "0.2.2"]


                 [cljs-ajax "0.7.2"
                  :exclusions [org.apache.httpcomponents/httpasyncclient]]

                 ;; Frontend UI libraries
                 [reagent "0.8.0-alpha2"]
                 [webjure/tuck "0.4.4"]
                 [cljsjs/react "15.6.1-2"]
                 [cljsjs/react-dom "15.6.1-2"]
                 [cljs-react-material-ui "0.2.48"]
                 [cljsjs/material-ui-chip-input "0.17.2-0"]
                 [figwheel "0.5.13"]
                 [cljsjs/react-leaflet "1.6.5-0" :exclusions [cljsjs/leaflet]]

                 [cljsjs/leaflet "1.2.0-0"]
                 ;[mappy "20180404"]
                 [funcool/bide "1.6.0"] ; URL router
                 [stylefy "1.0.1"]
                 [cljsjs/leaflet-draw "0.4.12-0"]
                 [cljsjs/nprogress "0.2.0-1"]
                 ;; Note: Sadly, no good clj/cljs library was found. There were good libs only for server-side rendering.
                 [cljsjs/marked "0.3.5-1"]

                 ;; Aika
                 [com.andrewmcveigh/cljs-time "0.5.0"]

                 ;; HTML/XML generation from Clojure data
                 [hiccup "1.0.5"]
                 ;; XML zippers
                 [org.clojure/data.zip "0.1.2"]

                 ;; GeoTools
                 [org.geotools/gt-shapefile "16.1"]
                 [org.geotools/gt-process-raster "16.1"]
                 [org.geotools/gt-epsg-wkt "16.1"]
                 [org.geotools/gt-geometry "16.1"]
                 [org.geotools/gt-xml "16.1"]
                 [org.geotools/gt-geojson "16.1"]

                 ;; Data/file formats and file handling
                 ;; JSON
                 [cheshire "5.8.0"]
                 ;; CSV
                 [org.clojure/data.csv "0.1.4"]
                 [cljsjs/jszip "3.1.3-0"]
                 [cljsjs/filesaverjs "1.3.3-0"]

                 ;; override old guava version from deps
                 [com.google.guava/guava "21.0"]]

  :profiles {:uberjar {:aot :all

                       ;; Prevent uberjar from cleaning cljs generated files
                       :auto-clean false}
             :dev {:dependencies [[org.clojure/test.check "0.10.0-alpha2"]]
                   :test-paths ["test/clj"]}}

  :repositories [["osgeo" "http://download.osgeo.org/webdav/geotools/"]
                 ["boundlessgeo" "https://repo.boundlessgeo.com/main/"]]
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.13"]]

  ;; Backend lähdekoodit: clj ja frontin kanssa jaettu cljc
  :source-paths ["src/clj" "src/cljc"]

  ;; ClojureScript buildit
  :cljsbuild {:builds
              [;; Paikallinen kehitys build
               {:id "dev"
                :source-paths ["src/cljs" "src/cljc" "../../mappy/src/cljs"]
                :figwheel {:on-jsload "ote.main/reload-hook"}
                :compiler {:optimizations :none
                           ;;:verbose true
                           :source-map true
                           :output-to "resources/public/js/ote.js"
                           :output-dir "resources/public/js/out"}}

               ;; Tuotantobuild advanced compilation
               {:id "prod"
                :source-paths ["src/cljs" "src/cljc"]
                :compiler {;;:pseudo-names true ; enable for debugging externs
                           :optimizations :advanced
                           :output-to "resources/public/js/ote.js"
                           :output-dir "resources/public/js/prod-out"
                           :source-map "resources/public/js/ote.js.map"
                           :closure-output-charset "US-ASCII"
                           :infer-externs true}}]}

  :clean-targets ^{:protect false}
  ["resources/public/js/ote.js" "resources/public/js" "target/classes"]

  :aliases {;; Alias for doing a full production build
            "production" ["do" "clean," "deps," "compile,"
                          "cljsbuild" "once" "prod,"
                          "postbuild,"
                          "uberjar"]

            "postbuild" ["run" "-m" "ote.tools.postbuild"]
            }
  :repl-options {:init-ns ote.main
                 :init (ote.main/start)}
  :main ote.main)
