(ns sir.bart
  (:use cheshire.core)
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.core.cache :as cache]
            [clojure.string :as str]))

(defn parse [s]
  (xml/parse
    (java.io.ByteArrayInputStream. (.getBytes s))))

(def bart-cache (atom (cache/ttl-cache-factory {} :ttl (* 1000 60))))

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

(def entrance-data {
  :12th [{:lat 37.804501, :lng -122.271252}, {:lat 37.804238, :lng -122.270772}, {:lat 37.803252, :lng -122.271736}, {:lat 37.803375, :lng -122.271966}, {:lat 37.802357, :lng -122.272301}, {:lat 37.802454, :lng -122.272535}, {:lat 37.803941, :lng -122.271312}]
  :16th []
  :19th [{:lat 37.808964, :lng -122.267841}, {:lat 37.808841, :lng -122.268503}, {:lat 37.808427, :lng -122.268512}, {:lat 37.807490, :lng -122.269092}, {:lat 37.806899, :lng -122.269464}, {:lat 37.807358, :lng -122.270033}]
  :24th []
  :ashb []
  :balb []
  :bayf []
  :cast []
  :civc []
  :cols []
  :colm []
  :conc []
  :daly []
  :dbrk []
  :dubl []
  :deln []
  :plza []
  :embr [{:lat 37.793536, :lng -122.395840}, {:lat 37.793682, :lng -122.396025}, {:lat 37.792788, :lng -122.396789}, {:lat 37.792901, :lng -122.396995}, {:lat 37.792046, :lng -122.397729}, {:lat 37.792184, :lng -122.397928}]
  :frmt []
  :ftvl []
  :glen []
  :hayw []
  :lafy []
  :lake []
  :mcar [{:lat 37.829356 :lng -122.266669}]
  :mlbr []
  :mont [{:lat 37.789378, :lng -122.401114}, {:lat 37.789190, :lng -122.401759}, {:lat 37.788489, :lng -122.402242}, {:lat 37.790529, :lng -122.400708}]
  :nbrk []
  :ncon []
  :oakl []
  :orin []
  :pitt []
  :phil []
  :powl [{:lat 37.786136, :lng -122.405590}, {:lat 37.786045, :lng -122.405405}, {:lat 37.785439, :lng -122.406469}, {:lat 37.785294, :lng -122.406331}, {:lat 37.784420, :lng -122.407399}, {:lat 37.784500, :lng -122.407643}, {:lat 37.783877, :lng -122.408595}, {:lat 37.783712, :lng -122.408359}]
  :rich []
  :rock []
  :sbrn []
  :sfia []
  :sanl []
  :shay []
  :ssan []
  :ucty []
  :wcrk []
  :wdub []
  :woak []
   })

(defn get-entrances-for-station [station]
  (get entrance-data (keyword (str/lower-case (:abbr station)))))
