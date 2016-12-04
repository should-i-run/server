(ns sir.handler
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)
  (:use compojure.core)
  (:use cheshire.core)
  (:use ring.util.response)
  (:require [compojure.handler :as handler]
            [ring.middleware.json :as middleware]
            [org.httpkit.client :as http]
            [ring.adapter.jetty :as jetty]
            [clojure.string :as str]
            [clojure.core.cache :as cache]
            [sir.goog :as goog]
            [sir.bart :as bart]
            [sir.muni :as muni]
            [sir.simpleBart :as simpleBart]
            [sir.bartStations :as bartStations]
            [sir.bartDirections :as bartDirections]
            [environ.core :refer [env]]
            [compojure.route :as route]))

(def goog-cache (atom (cache/ttl-cache-factory {} :ttl (* 1000 60 3))))

; TODO round lat and lon, combine
(defn cache-name [{:keys [:origin :dest]}]
  (-> (str (:lat origin) (:lng origin) (:lat dest) (:lng dest))
      (str/replace "." "_")))

; TODO
; remove routes without times after they are back from the second api request
(defn fetch-agency-data [trip]
  (let [trip-or-trips (cond
                        (= (:agency trip) "bart") (bart/fetch trip)
                        (= (:agency trip) "muni") (muni/fetch trip)
                        :else trip)]
    (if (vector? trip-or-trips)
      trip-or-trips
      (conj [] trip-or-trips))))

(defn same-trip? [tripA tripB]
  (if (= (:eolStationName tripA) (:eolStationName tripB))
    (= (:originStationName tripA) (:originStationName tripB))
    nil))

(defn trip-in-set? [trip trips]
  (some #(same-trip? % trip) trips))

(defn make-uniq [results]
  (remove nil? (reduce (fn [collector trip]
                          (if (trip-in-set? trip collector)
                            collector
                            (conj collector trip)))
                        []
                        results)))

(defn parse-results
  [{:keys [routes status]}]
  (let [trips
          (make-uniq
            (apply concat
              (map #(goog/parse-route %) routes)))]
    (let [all-timed-trips
            (reduce
              (fn [collector trip]
                (let [timed-trips (fetch-agency-data trip)]
                  (into collector timed-trips)))
              []
              trips)]
      all-timed-trips)))

(defn contains-keys? [coll & keys]
  (every? true? (map #(contains? coll %) keys)))

(defn valid-request?
  [body params]
  (or
    (contains-keys? params :startLat :startLon :destLat :destLon)
    (and (map? body) (contains-keys? body :origin :dest))))

(defn normalize-request [body params]
  (if (contains? params :startLat)
    {:origin {:lat (:startLat params) :lng (:startLon params)}
    :dest {:lat (:destLat params) :lng (:destLon params)}}
    body))

(defn fetch-trips
  [body params]
  (if (valid-request? body params)
    (let [req-data (normalize-request body params)]
      (let [url (goog/build-url req-data)]
        (let [data (if (cache/has? @goog-cache (cache-name req-data))
                     (cache/lookup @goog-cache (cache-name req-data))
                     (let [fresh-data @(http/get url)]
                       (swap! goog-cache assoc (cache-name req-data) fresh-data)
                       fresh-data))]
          (let [{:keys [status headers body error]} data]
            (if error
              (do (println "google api error: " url error)
                  {:status 418
                   :body "fail"})
              {:status 200
               :headers {"Content-Type" "application/json"}
               ; todo handle errors parsing results?
               :body (generate-string (parse-results (parse-string body true)))})))))
    {:status 400
     :body "bad request"}))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (POST "/stations" {body :body} (bartStations/get-closest-stations body))
  (POST "/" {body :body params :params} (fetch-trips body params))
  (POST "/bart" {body :body} (simpleBart/fetch-all body))
  (POST "/bart/directions" {body :body} (bartDirections/fetch-all body))
  (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (middleware/wrap-json-body {:keywords? true})
      (middleware/wrap-json-response)))

(defn -main []
  (let [port (Integer. (or (env :port) 3000))]
    (jetty/run-jetty app {:port port})))
