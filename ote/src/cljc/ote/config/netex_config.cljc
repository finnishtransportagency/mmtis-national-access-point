(ns ote.config.netex-config
  "Configuration values for the netex feature.
  This file contains constants which are not environment-specific, so they are defined here instead of hard-coding in
  implementation files. Motivation is to have constants in one place for better overview to the feature and its
  variability.")

(defn config []
  {:chouette {:work-dir "work/"
              :input-config-file "importGtfs.json"
              :export-config-file "exportNetex.json"
              :input-report-file "inputReport.json"
              :output-report-file "outputReport.json"
              :validation-report-file "validation_report.json"}})
