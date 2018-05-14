(ns ote.util.zip
  "Generate and read zip files."
  #?@(:cljs [(:require [cljsjs.jszip]
                       [cljsjs.filesaverjs])]
      :clj [(:require [clojure.java.io :as io])
            (:import (java.util.zip ZipOutputStream ZipEntry ZipInputStream))]))

(defn write-zip
  "Write a zip file. Content is a sequence of file descriptors.
  Each file descriptor is a map containing the name and data.
  To is a (platform dependent) target. In the browser it is a file name
  to download to. On the JVM it is an output stream."
  [content to]
  #?(:cljs (let [zip (js/JSZip.)]
             (doseq [{:keys [name data]} content]
               (.file zip name data))
             (-> zip
                 (.generateAsync #js {:type "blob"})
                 (.then #(js/saveAs % to))))
     :clj (with-open [out (ZipOutputStream. to)]
            (doseq [{:keys [name data]} content]
              (.putNextEntry out (ZipEntry. name))
              (.write out (.getBytes data "UTF-8"))
              (.closeEntry out)))))



#?(:clj
   (defn read-zip-with
     "Reads a zip file. Calls `file-callback` with each file in the zip.
  File callback is called with a map containing `:name` and `:input` keys."
     [input file-callback]
     (with-open [in (ZipInputStream. input)]
       (loop []
         (when-let [entry (.getNextEntry in)]
           (file-callback {:name (.getName entry)
                           :input in})
           (recur))))))
#?(:clj
   (defn read-zip
     "Read a zip file. Returns a sequence of file descriptors.
  Each file descriptor is a map containing the name and data."
     [input]
     (let [files (atom [])]
       (read-zip-with input (fn [{:keys [name input]}]
                              (swap! files conj {:name name
                                                 :data (with-open [out (java.io.ByteArrayOutputStream.)]
                                                         (io/copy input out)
                                                         (String. (.toByteArray out) "UTF-8"))})))
       @files)))
