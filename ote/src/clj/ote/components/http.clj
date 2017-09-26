(ns ote.components.http
  "HTTP-kit server"
  (:require [org.httpkit.server :as server]
            [com.stuartsierra.component :as component]
            [compojure.route :as route]
            [cognitect.transit :as transit]
            [ote.nap.cookie :as nap-cookie]))

(defn- serve-request [handlers req]
  ((apply some-fn handlers) req))

(defrecord HttpServer [config handlers]
  component/Lifecycle
  (start [this]
    (let [resources (route/resources "/")
          handler #(serve-request @handlers %)
          handler (if-let [auth-tkt (:auth-tkt config)]
                    (nap-cookie/wrap-check-cookie auth-tkt handler)
                    handler)]
      (assoc this ::stop
             (server/run-server
              (fn [req]
                (or (resources req)
                    (handler req)))
              config))))
  (stop [{stop ::stop :as this}]
    (stop)
    (dissoc this ::stop)))

(defn http-server
  "Create an HTTP server component with the given `config`."
  [config]
  (->HttpServer config (atom [])))

(defn publish!
  "Publish a new Ring `handler` to the HTTP-server.
  Requests are handled by trying each published handler in the order they are published until
  one of the handlers returns a response.

  Handlers must return `nil` for requests they aren't prepared to handle.


  Returns a 0-arity function that will remove this `handler` when called."
  [{handlers :handlers} handler]
  (swap! handlers conj handler)
  #(swap! handlers
          (fn [handlers]
            (filterv (partial not= handler) handlers))))

(defn transit-response
  "Return the given Clojure `data` as a Transit response with status code 200."
  [data]
  {:status 200
   :headers {"Content-Type" "application/json+transit"}
   :body (with-open [out (java.io.ByteArrayOutputStream.)]
           (transit/write (transit/writer out :json) data)
           (str out))})
