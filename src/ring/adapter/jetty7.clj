(ns ring.adapter.jetty7
  "Adapter for the Jetty7 webserver."
  (:import (org.eclipse.jetty.server.handler AbstractHandler)
           (org.eclipse.jetty.server Server Request Response)
           (org.eclipse.jetty.server.bio SocketConnector)
           (org.eclipse.jetty.server.ssl SslSocketConnector)
           (javax.servlet.http HttpServletRequest HttpServletResponse))
  (:require [ring.util.servlet :as servlet]))

(defn- proxy-handler
  "Returns an Jetty Handler implementation for the given Ring handler."
  [handler]
  (proxy [AbstractHandler] []
    (handle [target ^Request base-request request response]
      (let [request-map  (servlet/build-request-map base-request)
            response-map (handler request-map)]
        (when response-map
          (servlet/update-servlet-response response response-map)
          (.setHandled base-request true))))))

(defn- add-ssl-connector!
  "Add an SslSocketConnector to a Jetty Server instance."
  [^Server server options]
  (let [ssl-connector (SslSocketConnector.)]
    (doto ssl-connector
      (.setPort        (options :ssl-port 443))
      (.setKeystore    (options :keystore))
      (.setKeyPassword (options :key-password)))
    (when (options :truststore)
      (.setTruststore ssl-connector (options :truststore)))
    (when (options :trust-password)
      (.setTrustPassword ssl-connector (options :trust-password)))
    (.addConnector server ssl-connector)))

(defn- create-server
  "Construct a Jetty Server instance."
  [options]
  (let [connector (doto (SocketConnector.)
                    (.setPort (options :port 80))
                    (.setHost (options :host)))
        server    (doto (Server.)
                    (.addConnector connector)
                    (.setSendDateHeader true))]
    (when (or (options :ssl?) (options :ssl-port))
      (add-ssl-connector! server options))
    server))

(defn ^Server run-jetty
  "Serve the given handler according to the options.
  Options:
    :configurator   - A function called with the Server instance.
    :port
    :host
    :join?          - Block the caller: defaults to true.
    :ssl?           - Use SSL.
    :ssl-port       - SSL port: defaults to 443, implies :ssl?
    :keystore
    :key-password
    :truststore
    :trust-password"
  [handler options]
  (let [^Server s (create-server (dissoc options :configurator))]
    (when-let [configurator (:configurator options)]
      (configurator s))
    (doto s
      (.setHandler (proxy-handler handler))
      (.start))
    (when (:join? options true)
      (.join s))
    s))