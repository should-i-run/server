(ns sir.bart
  (:use cheshire.core)
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.core.cache :as cache]
            [clojure.string :as str]))

(defn parse [s]
  (xml/parse
    (java.io.ByteArrayInputStream. (.getBytes s))))

(def bart-cache (atom (cache/ttl-cache-factory {} :ttl (* 1000 60 3))))

(defn cache-item-name [{code :originStationCode}]
  code)

(defn gen-trips [times trip]
  (map
    (fn [minutes]
      ; minutes is either a number or "Leaving"
      (if (number? (read-string minutes))
        (into trip {:departureTime (+ (System/currentTimeMillis) (* (read-string minutes) 1000 60))})
        (into trip {:departureTime (+ (System/currentTimeMillis) (* 1000 60))})))
    times))

(defn get-minutes-from-etd [etd]
  (map
    #(get-in % [:content 0 :content 0])
    (filter
      #(= (:tag %) :estimate)
      (:content etd))))

(defn get-etd-for-eol [body station-code]
  (nth (filter (fn [etd]
                  (= (str/lower-case (get-in etd [:content 1 :content 0])) station-code))
               (->>
                body
                xml-seq
                (filter #(= (:tag %) :etd))))
        0
        ""))

(defn get-departure-times [body station-code]
  (do
    (if (= "" (get-etd-for-eol body station-code))
      (println "bart - no etd for eol" station-code body))
    (get-minutes-from-etd (get-etd-for-eol body station-code))))

(defn process-data [trip body]
  (gen-trips (get-departure-times body (:eolStationCode trip)) trip))

(defn build-url
  [trip]
  (str "http://api.bart.gov/api/etd.aspx?cmd=etd&orig="
       (:originStationCode trip)
       "&key=" (System/getenv "BART_API")))

(defn fetch [trip]
  (let [url (build-url trip)
        data (if (cache/has? @bart-cache (cache-item-name trip))
               (cache/lookup @bart-cache (cache-item-name trip))
               (let [fresh-data @(http/get url)]
                 (swap! bart-cache assoc (cache-item-name trip) fresh-data)
                 fresh-data))]
    (let [{body :body error :error} data]
      (if error
        (do (println "bart api error:" url error)
             [])
        (into [] (process-data trip (parse body)))))))

(def station-data {
  :12th-St-Oakland-City-Center "12th"
  :16th-St-Mission "16th"
  :19th-St-Oakland "19th"
  :24th-St-Mission "24th"
  :Ashby "ashb"
  :Balboa-Park "balb"
  :Bay-Fair "bayf"
  :Castro-Valley "cast"
  :Civic-Center-UN-Plaza "civc"
  :Coliseum-Oakland-Airport "cols"
  :Colma "colm"
  :Concord "conc"
  :Daly-City "daly"
  :Downtown-Berkeley "dbrk"
  :Dublin-Pleasanton "dubl"
  :El-Cerrito-del-Norte "deln"
  :El-Cerrito-Plaza "plza"
  :Embarcadero "embr"
  :Fremont "frmt"
  :Fruitvale "ftvl"
  :Glen-Park "glen"
  :Hayward "hayw"
  :Lafayette "lafy"
  :Lake-Merritt "lake"
  :MacArthur "mcar"
  :Millbrae "mlbr"
  :Montgomery-St "mont"
  :North-Berkeley "nbrk"
  :North-Concord-Martinez "ncon"
  :Oakland-Intl-Airport "oakl"
  :Orinda "orin"
  :Pittsburg-Bay-Point "pitt"
  :Pleasant-Hill "phil"
  :Powell-St "powl"
  :Richmond "rich"
  :Rockridge "rock"
  :San-Bruno "sbrn"
  :San-Francisco-Intl-Airport "sfia"
  :San-Leandro "sanl"
  :South-Hayward "shay"
  :South-San-Francisco "ssan"
  :Union-City "ucty"
  :Walnut-Creek "wcrk"
  :West-Dublin "wdub"
  :West-Oakland "woak" })

(defn normalize-name [string]
  (-> string
      (str/replace " Station" "")
      (str/replace " " "-")
      (str/replace "St." "St")
      (str/replace  "/" "-")
      (str/replace  "'" "")))

(defn station-lookup [station-name]
  (get station-data (keyword (normalize-name station-name))))
