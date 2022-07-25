(ns url-short.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [taoensso.carmine :as redis])
  (:import
   org.apache.commons.validator.routines.UrlValidator))

(def counter (atom 10000000))

(def validator (UrlValidator. (into-array ["http" "https"])))

(def redis-conn {:pool {} :spec {:uri "redis://127.0.0.1:6379"}})

(defmacro wcar* [& body] `(redis/wcar redis-conn ~@body))

(defn create-short-url [path]
  (let [rand-str (inc @counter)]
    (swap! counter inc)
    (wcar* nil
                (redis/set (str "/" rand-str) path))
    (str "http://localhost:8080/" rand-str)))

(defn handle-create [{:keys [json-params]}]
  (if (.isValid validator (str (:url json-params))) ; Drop '/'
    {:status 200 :body (create-short-url (:url json-params))}
    {:status 401 :body "Invalid Url provided"}))

(defn handle-redirect [{path :uri :as request}]
  (let [url (wcar* nil (redis/get path))]
    (if url
      {:status 302 :body "" :headers {"Location" url}}
      {:status 404 :body "Unknown destination."})))



(defn home-page
  [request]
  (ring-resp/response "A short url API!"))


;; Terse/Vector-based routes
(def routes
  `[[["/" {:get home-page
           :post handle-create}
      ^:interceptors [(body-params/body-params) http/json-body]
      ["/:id" {:get handle-redirect}
       ^:interceptors [(body-params/body-params)]]]]])


;; Consumed by url-short.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Tune the Secure Headers
              ;; and specifically the Content Security Policy appropriate to your service/application
              ;; For more information, see: https://content-security-policy.com/
              ;;   See also: https://github.com/pedestal/pedestal/issues/499
              ;;::http/secure-headers {:content-security-policy-settings {:object-src "'none'"
              ;;                                                          :script-src "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"
              ;;                                                          :frame-ancestors "'none'"}}

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ;;  This can also be your own chain provider/server-fn -- http://pedestal.io/reference/architecture-overview#_chain_provider
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8080
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false
                                        ;; Alternatively, You can specify you're own Jetty HTTPConfiguration
                                        ;; via the `:io.pedestal.http.jetty/http-configuration` container option.
                                        ;:io.pedestal.http.jetty/http-configuration (org.eclipse.jetty.server.HttpConfiguration.)
                                        }})
