(ns sir.simpleBart
  (:use cheshire.core)
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.core.cache :as cache]
            [clojure.string :as str]
            [sir.bartStations :as bartStations]
            [sir.bart :as bart]))

(defn parse
  [s]
  (xml/parse
    (java.io.ByteArrayInputStream. (.getBytes s))))

; {:tag :abbreviation, :attrs nil, :content [DALY]}
(defn get-code-from-etd
  [etd]
  (let [abbreviation (filter
                        #(= (:tag %) :abbreviation)
                        (:content etd))]
    (get-in (nth abbreviation 0) [:content 0])))

(defn get-minutes-from-etd
  [etd]
  (map
    #(get-in % [:content 0 :content 0])
    (filter
      #(= (:tag %) :estimate)
      (:content etd))))

(defn get-direction-from-etd
  [etd]
  (map
    #(get-in % [:content 0 :content 0])
    (filter
      #(= (:tag %) :estimate)
      (:content etd))))



(defn flatten-etd
  [etd]
  (reduce
    (fn
      [res entry]
      (if (= (:tag entry) :estimate)
          res
          (conj res {(:tag entry) (get-in entry [:content 0])})))
    {}
    (:content etd)))

(defn flatten-estimates
  [etd]
  (map
    flatten-etd
    (filter
      #(= (:tag %) :estimate)
      (:content etd))))

(defn flt
  [etd]
  (conj
    (flatten-etd etd)
    {:estimates (flatten-estimates etd)}))

(defn get-etds
  [body]
  (->>
    body
    xml-seq
    (filter #(= (:tag %) :etd))))

(defn gdt
  [body]
  (map
    flt
    (get-etds body)))

(defn get-departure-times [body]
  (do
    ;; (println (gdt body))
    (gdt body)))


(defn build-url
  [stationCode]
  (str "http://api.bart.gov/api/etd.aspx?cmd=etd&orig="
       stationCode
       "&key=" (or (System/getenv "BART_API") "ZELI-U2UY-IBKQ-DT35")))

(defn fetch-station
  [station]
  (let [url (build-url (:abbr station))
        stationCode (:abbr station)
        data (if (cache/has? @bart/bart-cache stationCode)
               (cache/lookup @bart/bart-cache stationCode)
               (let [fresh-data @(http/get url)]
                 (swap! bart/bart-cache assoc stationCode fresh-data)
                 fresh-data))]
    (let [{body :body error :error} data]
      (if error
        (do ;; (println "bart api error:" url error)
            {:status 418
             :body "bart api error"})
        (do
          ;; (println (build-url stationCode))
          (conj station
                {:departures
                            (get-departure-times (parse body))}))))))

(defn fetch-all
  [loc]
  {:status 200
    :headers {"Content-Type" "application/json"}
    :body (generate-string (map fetch-station (bartStations/get-closest-stations loc)))})
