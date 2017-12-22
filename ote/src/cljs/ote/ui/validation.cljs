(ns ote.ui.validation
  "OTE field validation.
  A field schema can define a vector of validation rules to apply.
  A validation rule is either a function which takes the value to validate
  and the whole form data and returns either `nil` (no errors) or a human
  readable string description of the validation errors.

  A validation can also be a vector where the first element is the name
  of a builtin validation rule and the rest are its arguments, if any."
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [ote.localization :refer [tr tr-key]]
            [ote.format :as fmt]))

;; max length 16 chars (optional plus followed by digits)
(def phone-number-regex #"^((\+?\d{0,15})|(\d{0,16}))$")

(defn empty-value? [arvo]
  (or (nil? arvo)
      (str/blank? arvo)))

;; validate-rule multimethod implements validation rule checking by keyword name
;; Parameters:
;; name = the keyword name of the field
;; data = the field value
;; row = the whole form/row (so that rules can be relations between fields)
;; table = when used in a grid, the whole grid data containing all rows
(defmulti validate-rule (fn [rule name data row table & options] rule))

(defmethod validate-rule :constant-notice [_ _ data _ _ & [message]]
  message)


(defmethod validate-rule :non-empty [_ _ data _ _ & [message]]
  (when (str/blank? data)
    (or message (tr [:common-texts :required-field]))))

(defmethod validate-rule :non-negative-if-key [_ _ data row _ & [key value message]]
  (when (and (= (key row) value)
             (< data 0))
    (or message "Value must not be negative")))

(defmethod validate-rule :non-empty-if-other-key-nil
  [_ _ data row _ & [other-key message]]
  (when (and (str/blank? data)
             (not (other-key row)))
    (or message "Value missing")))

(defmethod validate-rule :non-in-the-future [_ _ data _ _ & [message]]
  (when (and data (t/after? data (js/Date.)))
    (or message "Date cannot be in the future")))

(defmethod validate-rule :one-of [_ _ _ row _ & keys-and-message]
  (let [keys (if (string? (last keys-and-message))
               (butlast keys-and-message)
               keys-and-message)
        message (if (string? (last keys-and-message))
                 (last keys-and-message)

                 (str "Must be one of: "
                      (clojure.string/join ", "
                                           (map (comp clojure.string/capitalize name) keys))))]
    (when-not (some #(not (str/blank? (% row))) keys)
      message)))

(defmethod validate-rule :unique [_ name data _ table & [message]]
  (let [rows-by-value (group-by name (vals table))]
    (when (and (not (nil? data))
               (> (count (get rows-by-value data)) 1))
      (or message "Value must be unique"))))

(defmethod validate-rule :date-after-field [_ _ data row _ & [key message]]
  (when (and
          (key row)
          (t/before? data (key row)))
    (or message (str "Date must be after " (fmt/pvm (key row))))))

(defmethod validate-rule :date-after [_ _ data _ _ & [comparison-date message]]
  (when (and
          comparison-date
          (t/before? data comparison-date))
    (or message (str "Date must be after  " (fmt/pvm comparison-date)))))

(defmethod validate-rule :date-before [_ _ data _ _ & [comparison-date message]]
  (when (and data comparison-date
             (not (t/before? data comparison-date)))
    (or message (str "Date must be before " (fmt/pvm comparison-date)))))

(def year-month-and-day (juxt t/year t/month t/day))

(defmethod validate-rule :same-date [_ _ data _ _ & [comparison-date message]]
  (when (and data comparison-date
             (not= (year-month-and-day data)
                   (year-month-and-day comparison-date)))
    (or message (str "Date must " (fmt/pvm comparison-date)))))

;; Valid time HH:MM
(defmethod validate-rule :time [_ _ data _ _ & [message]]
  (when
    (and (not (empty-value? data)) (not (re-matches #"^(0[0-9]|1[0-9]|2[0-3])(:[0-5][0-9])?$" data)))
    (or message "Invalid time format")))

(defmethod validate-rule :number-range [_ _ data _ _ & [min-value max-value message]]
  (when-not (<= min-value data max-value)
    (or message (str "Number must be between " min-value " and " max-value))))

;; Valid Finnish Business ID (Y-tunnus)
(defmethod validate-rule :business-id [_ _ data _ _ & [message]]
  (and
    data
    (let [ ;; Split by separator
          [id check :as split] (str/split data #"-")
          ;; When numbers are removed, it sholud be ["" "-" nil]
          [id-part separator check-part] (str/split data #"\d+")]
      (when-not (and (= 9 (count data))
                     (= 2 (count split))
                     (= 7 (count id))
                     (= 1 (count check))
                     (empty? id-part)
                     (= "-" separator)
                     (nil? check-part))
        (or message
            ;; FIXME: use tr to translate
            "Y-tunnuksen pitää olla 7 numeroa, väliviiva, ja tarkastusnumero.")))))

;; Valid Finnish postal-code
(defmethod validate-rule :postal-code [_ _ data _ _ & [message]]
  (when
    (and (not (empty-value? data)) (not (re-matches #"^\d{5}$" data)))
    (or message (tr [:common-texts :invalid-postal-code]))
  ))

;; Validate that checkbox is checked
(defmethod validate-rule :checked? [_ _ data _ _ ]
  (when (not data)
    (tr [:common-texts :required-field])))

(defn validate-rules
  "Returns all validation errors for a field as a sequence. If the sequence is empty,
  validation passed without errors."
  [name data row table rules]
  (keep (fn [rule]
          (if (fn? rule)
            (rule data row)
            (let [[rule & options] rule]
              (apply validate-rule rule name data row table options))))
        rules))

(defn validate-row
  "Validate all fields of a single row/form of data.
  Returns a map of {field-name [errors]}.
  Type selects the validations to use and must be one of: `#{:validate :warn :notice}`.
  The default type is `:validate`."
  ([table row schemas] (validate-row table row schemas :validate))
  ([table row schemas type]
   (loop [v {}
          [s & schemas] schemas]
     (if-not s
       v
       (let [{:keys [name read]} s
             validoi (type s)]
         (if (empty? validoi)
           (recur v schemas)
           (let [errors (validate-rules name (if read
                                                 (read row)
                                                 (get row name))
                                          row table
                                          validoi)]
             (recur (if (empty? errors)
                      v
                      (assoc v name errors))
                    schemas))))))))

(defn missing-required-fields
  "Returns a sequence of schemas that are marked as required and are missing a value."
  [row skeema]
  (keep (fn [{:keys [required? read name type is-empty?] :as s}]
          (when (and required?
                     ((or is-empty? empty-value?)
                      (if read
                        (read row)
                        (get row name))))
            s))
        skeema))
