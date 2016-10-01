(ns sir.bartStations
  (:use cheshire.core)
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.core.cache :as cache]
            [clojure.string :as str]
            [clojure.math.numeric-tower :as math]
            [sir.bart :as bart]))

(defn parse
  [s]
  (xml/parse
    (java.io.ByteArrayInputStream. (.getBytes s))))

(def bart-station-cache (atom (cache/ttl-cache-factory {} :ttl (* 1000 60 60 24 7))))

(defn calc-distance
  [station lat lng]
  (let [station-lat (read-string (:gtfs_latitude station))
        station-lng (read-string (:gtfs_longitude station))]
        (math/sqrt
          (+ (math/expt (- station-lat lat) 2)
             (math/expt (- station-lng lng) 2)))))

(defn calc-distances
  [stations lat lng]
  (map
    (fn [station]
      (conj station
            {:distance (calc-distance station lat lng)}))
    stations))

(defn get-stations
  [body]
  (->>
    body
    xml-seq
    (filter #(= (:tag %) :station))
    (map :content)))


(defn flatten-station
  [station]
  (reduce
    (fn
      [res entry]
      (conj res {(:tag entry) (get-in entry [:content 0])}))
    {}
    station))

(defn find-closest-stations
  [body lat lng]
  (take 2
    (sort-by :distance
      (calc-distances
        (map flatten-station (get-stations body))
        lat lng))))

(defn build-url
  []
  (str "http://api.bart.gov/api/stn.aspx?cmd=stns"
       "&key=" (or (System/getenv "BART_API") "ZELI-U2UY-IBKQ-DT35")))

(defn get-closest-stations
  [loc]
  (do (println loc)
    (let [url (build-url)
          data (if (cache/has? @bart-station-cache :stations)
                 (cache/lookup @bart-station-cache :stations)
                 (let [fresh-data @(http/get url)]
                   (swap! bart-station-cache assoc :stations fresh-data)
                   fresh-data))]
      (let [{body :body error :error} data]
        (do
          (println (find-closest-stations (parse body) (:lat loc) (:lng loc)))
        (find-closest-stations (parse body) (:lat loc) (:lng loc)))))))
