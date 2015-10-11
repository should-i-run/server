(ns sir.muni
  (:import (java.net URLEncoder))
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.core.cache :as cache]
            [clojure.string :as str]))

(def muni-cache (atom (cache/ttl-cache-factory {} :ttl (* 1000 60 3))))

(defn cache-item-name [url]
  (-> url
      (str/replace (str "http://services.my511.org/Transit2.0/"
                        "GetNextDeparturesByStopName.aspx?token="
                        (System/getenv "MUNI_API")
                        "&agencyName=SF-MUNI&stopName=")
                    "")
      (str/replace "%" "")))

(defn url-safe [url]
  (some-> url str (URLEncoder/encode "UTF-8") (.replace "+" "%20")))

(defn parse [s]
  (xml/parse
    (java.io.ByteArrayInputStream. (.getBytes s))))

(defn station-url [name]
  (let [strings [["Metro" ""] ["/Outbd" " Outbound"] ["/Outbound" " Outbound"] ["/Inbd" " Inbound"] ["/Inbound" " Inbound"] ["/Downtn" " Inbound"] ["/Downtown" " Inbound"]]]
    (str/trim
      (reduce
        (fn [result [replacee replaceor]]
          (str/replace result replacee replaceor))
        name
        strings))))

(defn gen-trips [times trip]
  (map
    (fn [minutes]
      ; minutes is either a number or "Leaving"
      (if (number? (read-string minutes))
        (into trip {:departureTime (+ (System/currentTimeMillis) (* (read-string minutes) 1000 60))})
        (into trip {:departureTime (+ (System/currentTimeMillis) (* 1000 60))})))
    times))

(defn directions-match? [{eolStationName :eolStationName} muni-name]
  (if (.contains muni-name eolStationName)
    true
    (and (.contains muni-name "Downtown") (.contains eolStationName "Downtown"))))

(defn get-times-from-departure [direction]
  (map
    #(get-in % [:content 0])
    (get direction 0))) ; no idea why this works or is needed

(defn get-departures-for-direction [departures trip]
  (let [departure
    (reduce
      (fn [coll item]
        (if (directions-match? trip (get-in item [:content 0 :attrs :Name]))
          (conj coll (get-in item [:content 0 :content 0 :content 0 :content 0 :content]))
          coll))
      []
      (get-in departures [0 :content]))]
  (get-times-from-departure departure)))

(defn get-route-for-line [routes {line-code :lineCode}]
  (into [] (filter
    (fn [item]
      (= line-code (get-in item [:attrs :Code])))
    routes)))

(defn get-routes [routes]
  (let [route-or-routes (get-in routes [:content 0 :content 0 :content 0 :content])]
    (if (vector? route-or-routes)
      route-or-routes
      (conj [] route-or-routes))))

(defn get-departure-times [body trip]
  (get-departures-for-direction (get-route-for-line (get-routes body) trip) trip))

(defn process-data [trip body]
  (gen-trips (get-departure-times body trip) trip))

(defn build-url
  [trip]
  (str "http://services.my511.org/Transit2.0/GetNextDeparturesByStopName.aspx?token="
       (System/getenv "MUNI_API")
       "&agencyName=SF-MUNI&stopName="
       (str/replace (url-safe (station-url (:originStationName trip))) "%26" "and")))

(defn fetch [trip]
  (let [url (build-url trip)]
    (let [data (if (cache/has? @muni-cache (cache-item-name url))
                 (cache/lookup @muni-cache (cache-item-name url))
                 (let [fresh-data @(http/get (build-url trip))]
                   (swap! muni-cache assoc (cache-item-name url) fresh-data)
                   fresh-data))]
      (let [{body :body error :error} data]
        (if error
          (do (println "muni api error:" url error)
               [])
          (into [] (process-data trip (parse body))))))))
