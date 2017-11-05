(ns ote.db.transport-service
  "Database configurations for Transport Services"
  (:require [clojure.spec.alpha :as s]
            #?(:clj [ote.tietokanta.specql-db :refer [define-tables]])
            [specql.rel :as rel]
            [specql.transform :as xf]
            [specql.impl.registry]
            [ote.db.common]
            [specql.data-types]
            [ote.time]
            [ote.db.modification])
  #?(:cljs
     (:require-macros [ote.tietokanta.specql-db :refer [define-tables]])))

;; FIXME: specql doesn't define timestamp with time zone type
(s/def :specql.data-types/timestamptz any?)

(define-tables
  ;; Define ENUMs
  ["week_day" ::day (specql.transform/transform (specql.transform/to-keyword))]


  ["payment_method" ::payment_method (specql.transform/transform (specql.transform/to-keyword))]
  ["transport_provider_type" ::transport_provider_type (specql.transform/transform (specql.transform/to-keyword))]
  ["transport_service_subtype" ::transport_service_subtype (specql.transform/transform (specql.transform/to-keyword))]
  ["accessibility_tool" ::accessibility_tool (specql.transform/transform (specql.transform/to-keyword))]
  ["accessibility_info_facility" ::accessibility_info_facility (specql.transform/transform (specql.transform/to-keyword))]
  ["accessibility_facility" ::accessibility_facility (specql.transform/transform (specql.transform/to-keyword))]
  ["mobility_facility" ::mobility_facility (specql.transform/transform (specql.transform/to-keyword))]
  ["passenger_information_facility" ::passenger_information_facility]
  ["safety_facility" ::safety_facility]
  ["parking_facility" ::parking_facility (specql.transform/transform (specql.transform/to-keyword))]
  ["additional_services" ::additional_services  (specql.transform/transform (specql.transform/to-keyword))]
  ["pick_up_type" ::pick_up_type]
  ["brokerage_service_type" ::brokerage_service_type]

  ;; UDT tyypit
  ["localized_text" ::localized_text]
  ["service_link" ::service_link]
  ["service_hours" ::service_hours]
  ["price_class" ::price_class]
  ["terminal_information" ::terminal_information]
  ["passenger_transportation_info" ::passenger_transportation_info]
  ["pick_up_location" ::pick_up_location]
  ["rental_provider_informaton" ::rental_provider_informaton]
  ["parking_area" ::parking_area]
  ["parking_provider_information" ::parking_provider_information]
  ["brokerage_service" ::brokerage_service]
  ["brokerage_provider_informaton" ::brokerage_provider_informaton]

  ;; Tables
  ["external-interface-description" ::external-interface-description]
  ["transport-service" ::transport-service
   ote.db.modification/modification-fields
   {::provider (specql.rel/has-one ::transport-operator-id ::transport-operator ::id)}]
  ["operation_area" ::operation_area])

;; Create order for transport_type
(def transport-service-types [:terminal :passenger-transportation :rentals :parking :brokerage])

;; Create order for transport_type
(def passenger-transportation-sub-types [:taxi :request :schedule])

;; Create order for payment_method
(def payment-methods [:cash :debit-card :credit-card :mobilepay :contactless-payment :invoice :other])

;; Create order for additional_services
(def additional-services [:child-seat :animal-transport :other])

;; Create order for accessibility_tool
(def accessibility-tool [:wheelchair :walkingstick :audio-navigator :visual-navigator :passenger-cart
                          :pushchair :umbrella :buggy :other])

(def days [:MON :TUE :WED :THU :FRI :SAT :SUN])

;; Create order for accessibility-info-facility
(def information-service-accessibility [:audio-for-hearing-impaired :audio-information :visual-displays
                                  :displays-for-visually-impaired :large-print-timetables])

;; Create order for accessibility-facility
(def accessibility [:unknown :lift :escalator :travelator :ramp :stairs :shuttle :narrow-entrance :barrier
                             :pallet-access-low-floor :validator :other])

;; Create order for mobility-facility-facility
(def mobility [:unknown :low-floor :step-free-access :suitable-for-wheelchairs
               :suitable-for-heavily-disabled :boarding-assistance :onboard-assistance
               :unaccompanied-minor-assistance :tactile-patform-edges :tactile-guiding-strips :other])

(defn service-key-by-type
  "Returns the service column keyword for the given type enum value."
  [type]
  (case type
    :passenger-transportation ::passenger-transportation
    :terminal ::terminal
    :rentals ::rentals
    :parking ::parking
    :brokerage ::brokerage))
