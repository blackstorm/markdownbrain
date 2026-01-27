(ns markdownbrain.lucide-icon
  (:require
   [clojure.java.io :as io]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [selmer.filter-parser :as selmer.filter-parser]
   [selmer.util :as selmer.util]))

(def ^:private icon-resource-prefix "templates/lucide/icons/")
(def ^:private icon-cache (atom {}))
(def ^:private alias-cache (atom nil))
(def ^:private installed-formatter (atom nil))
(def ^:private prior-missing-formatter (atom nil))
(def ^:private prior-filter-missing-values (atom nil))

(defn- escape-html-attr [s]
  (str/escape (str s)
              {\& "&amp;"
               \< "&lt;"
               \> "&gt;"
               \" "&quot;"
               \' "&#x27;"}))

(defn- valid-icon-name? [icon-name]
  (boolean (re-matches #"[a-z0-9-]+" (str icon-name))))

(def ^:private legacy-aliases
  {"check-circle" "circle-check-big"
   "alert-circle" "circle-alert"
   "alert-triangle" "triangle-alert"
   "more-vertical" "ellipsis-vertical"})

(defn- base-resource-url []
  ;; Use a known-present file to locate the resources directory for icons.
  (io/resource (str icon-resource-prefix "plus.svg")))

(defn- list-icon-json-resource-paths []
  (let [url (base-resource-url)]
    (when-not url
      (throw (ex-info "lucide_icon: unable to locate icons resources (missing plus.svg)" {})))
    (case (.getProtocol ^java.net.URL url)
      "file"
      (let [file (io/file (.toURI ^java.net.URL url))
            dir (.getParentFile ^java.io.File file)]
        (->> (.listFiles ^java.io.File dir)
             (keep (fn [^java.io.File f]
                     (let [name (.getName f)]
                       (when (str/ends-with? name ".json")
                         (str icon-resource-prefix name)))))
             vec))

      "jar"
      (let [^String spec (.getFile ^java.net.URL url)
            bang (.indexOf spec "!")
            jar-path (subs spec 0 bang)
            jar-path (str/replace jar-path #"^file:" "")
            jar-path (java.net.URLDecoder/decode jar-path "UTF-8")
            jar-prefix (subs spec (inc bang))
            icons-prefix (str/replace jar-prefix #"plus\.svg$" "")]
        (with-open [jar (java.util.jar.JarFile. jar-path)]
          (->> (enumeration-seq (.entries jar))
               (keep (fn [^java.util.jar.JarEntry entry]
                       (let [name (.getName entry)]
                         (when (and (str/starts-with? name icons-prefix)
                                    (str/ends-with? name ".json"))
                           name))))
               vec)))

      (throw (ex-info (str "lucide_icon: unsupported resource protocol: " (.getProtocol ^java.net.URL url))
                      {:protocol (.getProtocol ^java.net.URL url)})))))

(defn- build-alias-map []
  (let [paths (list-icon-json-resource-paths)]
    (reduce
     (fn [acc path]
       (try
         (let [base (-> path (str/replace #"^.*\/" "") (str/replace #"\.json$" ""))
               parsed (some-> (io/resource path) slurp (json/read-str))
               aliases (get parsed "aliases")]
           (if (sequential? aliases)
             (reduce
              (fn [m alias]
                (let [alias-name (get alias "name")]
                  (if (string? alias-name)
                    (assoc m alias-name base)
                    m)))
              acc
              aliases)
             acc))
         (catch Exception e
           (log/debug e "lucide_icon: failed to parse alias file" path)
           acc)))
     {}
     paths)))

(defn- resolve-icon-name [icon-name]
  (let [direct (io/resource (str icon-resource-prefix icon-name ".svg"))]
    (cond
      direct icon-name
      :else
      (or (get legacy-aliases icon-name)
          (let [aliases (or @alias-cache
                            (let [m (build-alias-map)]
                              (reset! alias-cache m)
                              m))]
            (get aliases icon-name))))))

(defn- read-icon-svg! [icon-name]
  (if-let [cached (get @icon-cache icon-name)]
    cached
    (let [resolved (resolve-icon-name icon-name)
          resource-path (when resolved (str icon-resource-prefix resolved ".svg"))
          url (when resource-path (io/resource resource-path))]
      (if-not url
        (do
          (log/warn "lucide_icon: icon not found:" (str icon-name ".svg (or alias)"))
          (swap! icon-cache assoc icon-name "")
          "")
        (let [svg (slurp url)]
          (swap! icon-cache assoc icon-name svg)
          svg)))))

(defn- normalize-class [class-name]
  (->> (str/split (str (or class-name "")) #"\s+")
       (map str/trim)
       (remove str/blank?)
       distinct
       (str/join " ")))

(defn- inject-svg-attrs [svg {:keys [icon-name class-name aria-label]}]
  (let [base-classes (normalize-class (str/join " " (remove str/blank? ["lucide" (str "lucide-" icon-name) class-name])))
        attrs (str
               (when-not (str/blank? base-classes)
                 (str " class=\"" (escape-html-attr base-classes) "\""))
               " data-lucide=\"" (escape-html-attr icon-name) "\""
               (if (str/blank? aria-label)
                 " aria-hidden=\"true\" focusable=\"false\""
                 (str " role=\"img\" aria-label=\"" (escape-html-attr aria-label) "\"")))]
    (-> svg
        (str/replace-first #"(?i)<svg\b" (str "<svg" attrs)))))

(defn- split-fn-args
  "Splits a comma-separated arg list, respecting double quotes.
   Returns a vector of raw arg strings (trimmed), without removing quotes."
  [s]
  (let [s (or s "")
        n (count s)]
    (loop [i 0
           in-string? false
           escaped? false
           buf (StringBuilder.)
           out []]
      (if (>= i n)
        (let [final (str/trim (.toString buf))]
          (cond-> out (not (str/blank? final)) (conj final)))
        (let [ch (.charAt ^String s i)]
          (cond
            escaped?
            (do (.append buf ch)
                (recur (inc i) in-string? false buf out))

            (and in-string? (= ch \\))
            (do (.append buf ch)
                (recur (inc i) in-string? true buf out))

            (= ch \")
            (do (.append buf ch)
                (recur (inc i) (not in-string?) false buf out))

            (and (not in-string?) (= ch \,))
            (let [token (str/trim (.toString buf))]
              (.setLength buf 0)
              (recur (inc i) in-string? false buf (cond-> out (not (str/blank? token)) (conj token))))

            :else
            (do (.append buf ch)
                (recur (inc i) in-string? false buf out))))))))

(defn- resolve-arg-value
  "Resolves an argument from lucide_icon(...).
   - \"literal strings\" are parsed as EDN
   - everything else is treated as a Selmer filter/body (variables, accessors, filters)."
  [arg context-map]
  (let [arg (some-> arg str/trim)]
    (cond
      (str/blank? arg) nil
      (and (str/starts-with? arg "\"") (str/ends-with? arg "\""))
      (try
        (clojure.edn/read-string arg)
        (catch Exception _
          (subs arg 1 (dec (count arg)))))
      :else
      ((selmer.filter-parser/compile-filter-body arg false) context-map))))

(defn- render-lucide-icon
  "Renders a Lucide icon from `server/resources/templates/lucide/icons/*.svg`.
   Supports:
     {{ lucide_icon(\"plus\") }}
     {{ lucide_icon(\"plus\", \"icon-sm\") }}
     {{ lucide_icon(icon, \"icon-sm\") }}
     {{ lucide_icon(icon, \"icon-sm\", \"Accessible label\") }}"
  [args-str context-map]
  (let [[name-arg class-arg label-arg] (split-fn-args args-str)
        icon-name (some-> (resolve-arg-value name-arg context-map) str str/trim)
        class-name (some-> (resolve-arg-value class-arg context-map) str str/trim)
        aria-label (some-> (resolve-arg-value label-arg context-map) str str/trim)]
    (cond
      (str/blank? icon-name) ""
      (not (valid-icon-name? icon-name))
      (do
        (log/warn "lucide_icon: invalid icon name:" (pr-str icon-name))
        "")
      :else
      (let [svg (read-icon-svg! icon-name)]
        (if (str/blank? svg)
          ""
          (inject-svg-attrs svg {:icon-name icon-name
                                 :class-name class-name
                                 :aria-label aria-label}))))))

(defn init!
  "Installs a Selmer missing-value formatter that recognizes lucide_icon(...) calls."
  []
  (let [current (var-get #'selmer.util/*missing-value-formatter*)]
    (when (not= current @installed-formatter)
      (reset! prior-missing-formatter current)
      (reset! prior-filter-missing-values (var-get #'selmer.util/*filter-missing-values*))
      (let [formatter
            (fn [tag context-map]
              (try
                (if (and (= :filter (:tag-type tag))
                         (string? (:tag-value tag)))
                  (let [tag-value (:tag-value tag)]
                    (if-let [[_ args] (re-matches #"(?is)^\s*lucide_icon\s*\(\s*(.*)\s*\)\s*$" tag-value)]
                      (render-lucide-icon args context-map)
                      (@prior-missing-formatter tag context-map)))
                  (@prior-missing-formatter tag context-map))
                (catch Exception e
                  (log/error e "lucide_icon: failed to render")
                  (@prior-missing-formatter tag context-map))))]
        (reset! installed-formatter formatter)
        (selmer.util/set-missing-value-formatter!
         formatter
         :filter-missing-values @prior-filter-missing-values)))))
