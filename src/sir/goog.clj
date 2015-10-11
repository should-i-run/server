
(ns sir.goog
  (:require
    [sir.bart :as bart]
    [clojure.string :as str]))

(defn build-url
  [{origin :origin dest :dest}]
  (str "https://maps.googleapis.com/maps/api/directions/json?origin="
       (origin :lat)
       ","
       (origin :lng)
       "&destination="
       (dest :lat)
       ","
       (dest :lng)
       "&key="
       (System/getenv "GOOGLE_API")
       "&departure_time="
       (quot (System/currentTimeMillis) 1000)
       "&mode=transit&alternatives=true"))

;; Result object:
;; originStationName
;; originStationLatLon {:lat :lon}
;; departureTime
;; eolStationName
;; agency
;; lineName
;; lineCode
;; agency

(defn get-departure-time [step]
  (* (get-in step [:transit_details :departure_time :value]) 1000))

(defn get-line-name [step]
  (get-in step [:transit_details :line :name]))

(defn get-line-code [step]
  (get-in step [:transit_details :line :short_name]))

(defn get-transit-type [step]
  (get-in step [:transit_details :line :vehicle :type]))

(defn get-origin-station-name [step]
  (get-in step [:transit_details :departure_stop :name]))

(defn get-origin-station-loc [{start :start_location}]
  {:lat (:lat start)
   :lon (:lng start)})

(defn get-eol-station-name [step]
  (let [strings ["Train towards", "Metro rail towards", "Bus towards"]]
    (str/trim
      (reduce
        (fn [result replacer]
          (str/replace result replacer ""))
        (:html_instructions step)
        strings))))

(defn process-caltrain [step]
  { :originStationName (get-origin-station-name step)
    :originStationLatLon (get-origin-station-loc step)
    :departureTime (get-departure-time step)
    :eolStationName (get-eol-station-name step)
    :lineName (get-line-name step)
    :agency "caltrain"})

(defn process-bart [step]
  { :originStationName (get-origin-station-name step)
    :originStationCode (bart/station-lookup (get-origin-station-name step))
    :originStationLatLon (get-origin-station-loc step)
    :eolStationName (get-eol-station-name step)
    :eolStationCode (bart/station-lookup (get-eol-station-name step))
    :lineName (get-line-name step)
    :agency "bart"})

(defn process-muni [step]
  { :originStationName (get-origin-station-name step)
    :originStationLatLon (get-origin-station-loc step)
    :eolStationName (get-eol-station-name step)
    :lineName (get-line-name step)
    :lineCode (get-line-code step)
    :transitType (get-transit-type step)
    :agency "muni"})

(defn agency-name-from-step [step]
  (if-let [agency-name (:name (get (get-in step [:transit_details :line :agencies]) 0))]
    agency-name
    nil))

(defn parse-step [step]
  (let [name (agency-name-from-step step)]
    (cond
      (= name "San Francisco Municipal Transportation Agency") (process-muni step)
      (= name "Bay Area Rapid Transit") (process-bart step)
      (= name "Caltrain") (process-caltrain step))))



(defn remove-buses [route]
  (reduce
    (fn [collector step]
      (if (not= (:transitType step) "BUS")
        (conj collector step)
        collector))
    []
    route))

(defn all-bart? [route]
  (every? #(= (:agency %) "bart") route))

(defn any-caltrain? [route]
  (some #(= (:agency %) "caltrain") route))

(defn caltrainify [route]
  (filter #(= (:agency %) "caltrain") route))

; TODO:
; filter out routes where the bus is too long, routes where supported transit type is after second
; Also only take the first supported transit type, after caltrainify
(defn filter-steps [route]
  (cond
    (= (count route) 1) route
    (any-caltrain? route) (caltrainify route)
    (all-bart? route) (conj [] (first route))
    :else route))

(defn parse-route [route]
  (let [trips (reduce
                (fn [collector step]
                  (let [trip (parse-step step)]
                    (if trip
                      (conj collector trip)
                      collector)))
                []
                (:steps (get (:legs route) 0)))]
    (remove nil? (filter-steps (filter identity trips)))))
