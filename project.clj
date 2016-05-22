(defproject sir "0.1.1"
  :description "Backend for Should I Run"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [ring/ring-json "0.1.2"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [c3p0/c3p0 "0.9.1.2"]
                 [http-kit "2.1.16"]
                 [javax.servlet/servlet-api "2.5"]
                 [environ "1.0.0"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [cheshire "4.0.3"]]
  :plugins [[lein-ring "0.8.13"]]
  :ring {:handler sir.handler/app
         :main sir.handler}
  :uberjar-name "should-i-run.jar"
  :profiles
   {:uberjar {:aot :all}
   :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
