(defproject ote "0.1-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
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
                 [http-kit "2.3.0"]
                 [bk/ring-gzip "0.2.1"]
                 [ring/ring-anti-forgery "1.1.0"]
                 [clj-http "3.7.0"]
                 [commons-fileupload/commons-fileupload "1.3.3"]
                 [javax.servlet/javax.servlet-api "4.0.0"]

                 ;; Email
                 [com.draines/postal "2.0.2"]

                 ;; Routing library for publishing services
                 [compojure "1.6.0"]

                 ;; Password hashing
                 [buddy/buddy-hashers "1.3.0"]
                 ;; File hash
                 [digest "1.4.8"]

                 ;; Cache libraries
                 [org.clojure/core.cache "0.7.1"]

                 ;; Transit tietomuoto
                 [com.cognitect/transit-clj "0.8.313"]
                 [com.cognitect/transit-cljs "0.8.256"]

                 ;; Lightweight scheduler
                 [jarohen/chime "0.2.2"]


                 [cljs-ajax "0.7.2"
                  :exclusions [org.apache.httpcomponents/httpasyncclient]]

                 ;; Frontend UI libraries
                 [reagent "0.8.1" :exclusions [cljsjs/react cljsjs/react-dom]]
                 [webjure/tuck "20181204"]
                 [cljsjs/react "16.8.3-0"]
                 [cljsjs/react-dom "16.8.3-0"]
                 [cljsjs/chartjs "2.7.3-0"]
                 [sablono "0.8.5"]
                 [rum "0.11.3"]
                 [cljsjs/material-ui "3.9.1-0"]
                 [cljsjs/material-ui-icons "3.0.1-0"]
                 [cljsjs/react-leaflet "1.6.5-0" :exclusions [cljsjs/leaflet]]
                 [cljsjs/leaflet "1.2.0-0"]
                 [funcool/bide "1.6.0"] ; URL router
                 [stylefy "1.12.0"]
                 [cljsjs/leaflet-draw "0.4.12-0"]
                 [cljsjs/nprogress "0.2.0-1"]
                 ;; Note: Sadly, no good clj/cljs library was found. There were good libs only for server-side rendering.
                 [cljsjs/marked "0.3.5-1"]
                 [data-frisk-reagent "0.4.5"]

                 ;; Aika
                 [com.andrewmcveigh/cljs-time "0.5.0"]

                 ;; HTML/XML generation from Clojure data
                 [hiccup "1.0.5"]
                 ;; XML zippers
                 [org.clojure/data.zip "0.1.2"]

                 ;; GeoTools
                 [org.geotools/gt-epsg-wkt "20.0"]
                 [org.geotools/gt-geometry "20.0"]

                 ;; Data/file formats and file handling
                 ;; JSON
                 [cheshire "5.8.0"]
                 ;; CSV
                 [org.clojure/data.csv "0.1.4"]
                 [cljsjs/jszip "3.1.3-0"]
                 [cljsjs/filesaverjs "1.3.3-0"]

                 ;; Amazon Web Services
                 [amazonica "0.3.121"
                  :exclusions [com.amazonaws/aws-java-sdk
                               com.amazonaws/amazon-kinesis-client]]
                 [com.amazonaws/aws-java-sdk-core "1.11.312"]
                 [com.amazonaws/aws-java-sdk-s3 "1.11.312"]

                 ;; override old guava version from deps
                 [com.google.guava/guava "21.0"]
                 #_[spec-provider "0.4.14"]]

  :profiles {:uberjar {:aot :all

                       ;; Prevent uberjar from cleaning cljs generated files
                       :auto-clean false}
             :dev {:dependencies [[org.clojure/test.check "0.10.0-alpha2"]
                                  [webjure/json-schema "0.7.4"]]
                   :test-paths ["test/clj"]}}

  :repositories [["osgeo" "https://download.osgeo.org/webdav/geotools/"]
                 ["boundlessgeo" "https://repo.boundlessgeo.com/main/"]]
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.13"]
            [lein-kibit "0.1.6"]]

  ;; Backend lähdekoodit: clj ja frontin kanssa jaettu cljc
  :source-paths ["src/clj" "src/cljc"]

  ;; ClojureScript buildit
  :cljsbuild {:builds
              [;; Paikallinen kehitys build
               {:id "dev"
                :source-paths ["src/cljs" "src/cljc"]
                :figwheel {:on-jsload "ote.main/reload-hook"}
                :compiler {:optimizations :none
                           ;; :verbose true
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
                 :init (ote.main/start)
                 :host "localhost"}
  :main ote.main
  :figwheel {:server-ip "localhost"
             :nrepl-host "localhost"
             :server-logfile "/tmp/logs/figwheel-logfile.log"})
