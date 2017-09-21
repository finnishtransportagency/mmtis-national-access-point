(defproject ote "0.1-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0-beta1"]
                 [org.clojure/clojurescript "1.9.908"]

                 ;; Komponenttikirjasto
                 [com.stuartsierra/component "0.3.2"]

                 ;; Logitus (clj + cljs)
                 [com.taoensso/timbre "4.10.0"]

                 ;; PostgreSQL JDBC ajuri, yhteyspooli ja muut apukirjastot
                 [org.postgresql/postgresql "42.1.4"]
                 [com.zaxxer/HikariCP "2.6.1"]
                 [org.clojure/java.jdbc "0.7.1"]
                 [webjure/jeesql "0.4.6"]
                 [specql "0.7.0-alpha3"]

                 ;; http-kit HTTP server (and client)
                 [http-kit "2.2.0"]

                 ;; Routing library for publishing services
                 [compojure "1.6.0"]

                 ;; Transit tietomuoto
                 [com.cognitect/transit-clj "0.8.300"]
                 [com.cognitect/transit-cljs "0.8.239"]

                 [cljs-ajax "0.7.2"]

                 ;; Frontin UI-kirjastot
                 [reagent "0.7.0"]
                 [webjure/tuck "0.4.1"]
                 [cljsjs/react "15.6.1-1"]
                 [cljsjs/react-dom "15.6.1-1"]
                 [cljs-react-material-ui "0.2.48"]
                 [figwheel "0.5.13"]

                 ;; Aika
                 [com.andrewmcveigh/cljs-time "0.5.0"]


                 ;; jostain tulee vanha guava, ylikirjoitetaan
                 [com.google.guava/guava "21.0"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.13"]]

  ;; Backend lähdekoodit: clj ja frontin kanssa jaettu cljc
  :source-paths ["src/clj" "src/cljc"]

  ;; ClojureScript buildit
  :cljsbuild {:builds
              [;; Paikallinen kehitys build
               {:id "dev"
                :source-paths ["src/cljs" "src/cljc"]
                :figwheel {:on-jsload "ote.main/reload-hook"}
                :compiler {:optimizations :none
                           :source-map true
                           :output-to "resources/public/js/ote.js"
                           :output-dir "resources/public/js/out"}}

               ;; Tuotantobuild advanced compilation
               {:id "prod"
                :source-paths ["src/cljs" "src/cljc"]
                :compiler {:optimizations :advanced
                           :output-to "resources/public/js/ote.js"
                           :source-map "resources/public/js/ote.js.map"}}]}

  :main ote.main)
