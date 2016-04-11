(ns sir.simpleBart
  (:use cheshire.core)
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.core.cache :as cache]
            [clojure.string :as str]
            [sir.bart :as bart]))

(defn parse [s]
  (xml/parse
    (java.io.ByteArrayInputStream. (.getBytes s))))

; {:tag :abbreviation, :attrs nil, :content [DALY]}
(defn get-code-from-etd [etd]
  (let [abbreviation (filter
                        #(= (:tag %) :abbreviation)
                        (:content etd))]
    (get-in (nth abbreviation 0) [:content 0 ])))

(defn get-minutes-from-etd [etd]
  (map
    #(get-in % [:content 0 :content 0])
    (filter
      #(= (:tag %) :estimate)
      (:content etd))))

(defn get-etds [body]
  (->>
    body
    xml-seq
    (filter #(= (:tag %) :etd))))

(defn get-departure-times [body]
  (map
    (fn [etd]
      { :code (get-code-from-etd etd)
      :departures (get-minutes-from-etd etd)})
    (get-etds body)))

(defn build-url
  [originStationCode]
  (str "http://api.bart.gov/api/etd.aspx?cmd=etd&orig="
       originStationCode
       "&key=" (System/getenv "BART_API") "ZELI-U2UY-IBKQ-DT35"))

(defn fetch [{originStationCode :origin}]
  (let [url (build-url originStationCode)
        data (if (cache/has? @bart/bart-cache originStationCode)
               (cache/lookup @bart/bart-cache originStationCode)
               (let [fresh-data @(http/get url)]
                 (swap! bart/bart-cache assoc originStationCode fresh-data)
                 fresh-data))]
    (let [{body :body error :error} data]
      (if error
        (do (println "bart api error:" url error)
            {:status 418
             :body "bart api error"})
        (do
          (println (build-url originStationCode))
          {:status 200
            :headers {"Content-Type" "application/json"}
            :body (generate-string (get-departure-times (parse body)))})))))
