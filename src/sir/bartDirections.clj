(ns sir.bartDirections
  (:use cheshire.core)
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.core.cache :as cache]
            [clojure.string :as str]
            [clojure.math.numeric-tower :as math]
            [sir.bart :as bart]))

(defn parse-trips
  [body]
  (get-in (parse-string (str/replace body "@" "") true) [:root :schedule :request :trip]))

(defn get-trips
  [{body :body error :error}]
  (if error
    {:error "error"}
    (parse-trips body)))

;; http://api.bart.gov/api/sched.aspx?cmd=depart&orig=ASHB&dest=CIVC&date=now&key=MW9S-E7SL-26DU-VV8V&json=y
(defn build-url
  [startCode endCode]
  (str "http://api.bart.gov/api/sched.aspx?cmd=depart&json=y&date=now"
       "&orig=" startCode
       "&dest=" endCode
       "&key=" (or (System/getenv "BART_API") "ZELI-U2UY-IBKQ-DT35")))

(defn get-data
  [startCode endCode]
  @(http/get (build-url startCode endCode)))

(defn fetch-all
  [trips]
  {:status 200
    :headers {"Content-Type" "application/json"}
    :body (generate-string
            (map
              (fn [{startCode :startCode endCode :endCode}]
                (get-trips (get-data startCode endCode)))
              trips))})
