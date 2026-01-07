(ns markdownbrain.object-store
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [markdownbrain.config :as config]
            [cognitect.aws.client.api :as aws]
            [cognitect.aws.credentials :as creds]))

(defonce ^:private s3-client (atom nil))

(defn parse-endpoint
  "Parse endpoint URL into hostname and port components.
   e.g., 'http://localhost:9000' -> {:hostname 'localhost' :port 9000}"
  [endpoint]
  (let [without-proto (str/replace endpoint #"^https?://" "")
        parts (str/split without-proto #":")
        hostname (first parts)
        port-str (when (> (count parts) 1)
                   (str/replace (second parts) #"/.*" ""))
        port (if (str/blank? port-str) 9000 (Integer/parseInt port-str))]
    {:hostname hostname :port port}))

(defn- get-client []
  (when-not @s3-client
    (let [{:keys [endpoint access-key secret-key region]} (config/s3-config)]
      (when (and endpoint access-key secret-key)
        (let [{:keys [hostname port]} (parse-endpoint endpoint)]
          (reset! s3-client
                  (aws/client {:api :s3
                               :region region
                               :endpoint-override {:protocol :http
                                                   :hostname hostname
                                                   :port port}
                               :credentials-provider (creds/basic-credentials-provider
                                                       {:access-key-id access-key
                                                        :secret-access-key secret-key})}))))))
  @s3-client)

(defn bucket-name
  "Returns the single S3 bucket name for all vaults."
  []
  (:bucket (config/s3-config)))

(defn- bucket-exists? []
  (when-let [client (get-client)]
    (let [bucket (bucket-name)
          result (aws/invoke client {:op :HeadBucket
                                     :request {:Bucket bucket}})]
      (if (= :cognitect.anomalies/unavailable (:cognitect.anomalies/category result))
        (throw (ex-info "S3 connection failed" {:endpoint (:endpoint (config/s3-config))
                                                 :error (:cognitect.anomalies/message result)}))
        (not (:cognitect.anomalies/category result))))))

(defn- create-bucket! []
  (when-let [client (get-client)]
    (let [bucket (bucket-name)
          result (aws/invoke client {:op :CreateBucket
                                     :request {:Bucket bucket}})]
      (cond
        (= :cognitect.anomalies/unavailable (:cognitect.anomalies/category result))
        (throw (ex-info "S3 connection failed" {:endpoint (:endpoint (config/s3-config))
                                                 :error (:cognitect.anomalies/message result)}))
        
        (:cognitect.anomalies/category result)
        (log/error "Failed to create bucket:" bucket result)
        
        :else
        (log/info "Created S3 bucket:" bucket)))))

(defn ensure-bucket!
  "Ensure the S3 bucket exists. Called once at application startup."
  []
  (when (get-client)
    (if (bucket-exists?)
      (log/info "S3 bucket exists:" (bucket-name))
      (create-bucket!))))

(defn vault-prefix
  "Returns the object key prefix for a vault: {vault-id}/"
  [vault-id]
  (str (str/replace vault-id "-" "") "/"))

(defn put-object!
  [vault-id object-key content content-type]
  (when-let [client (get-client)]
    (let [bucket (bucket-name)
          full-key (str (vault-prefix vault-id) object-key)
          body (if (bytes? content) content (.getBytes content "UTF-8"))]
      (let [result (aws/invoke client {:op :PutObject
                                       :request {:Bucket bucket
                                                 :Key full-key
                                                 :Body body
                                                 :ContentType content-type}})]
        (if (:cognitect.anomalies/category result)
          (do (log/error "Failed to put object:" result) nil)
          (do (log/debug "Put object:" bucket full-key) result))))))

(defn get-object [vault-id object-key]
  (when-let [client (get-client)]
    (let [bucket (bucket-name)
          full-key (str (vault-prefix vault-id) object-key)
          result (aws/invoke client {:op :GetObject
                                     :request {:Bucket bucket
                                               :Key full-key}})]
      (if (:cognitect.anomalies/category result)
        (do (log/debug "Object not found:" bucket full-key) nil)
        result))))

(defn delete-object! [vault-id object-key]
  (when-let [client (get-client)]
    (let [bucket (bucket-name)
          full-key (str (vault-prefix vault-id) object-key)
          result (aws/invoke client {:op :DeleteObject
                                     :request {:Bucket bucket
                                               :Key full-key}})]
      (if (:cognitect.anomalies/category result)
        (do (log/error "Failed to delete object:" result) nil)
        (do (log/debug "Deleted object:" bucket full-key) result)))))

(defn head-object [vault-id object-key]
  (when-let [client (get-client)]
    (let [bucket (bucket-name)
          full-key (str (vault-prefix vault-id) object-key)
          result (aws/invoke client {:op :HeadObject
                                     :request {:Bucket bucket
                                               :Key full-key}})]
      (if (:cognitect.anomalies/category result)
        nil
        result))))

(defn object-exists? [vault-id object-key]
  (some? (head-object vault-id object-key)))

(defn delete-vault-objects!
  "Delete all objects for a vault by listing and deleting objects with vault prefix."
  [vault-id]
  (when-let [client (get-client)]
    (let [bucket (bucket-name)
          prefix (vault-prefix vault-id)]
      (loop []
        (let [result (aws/invoke client {:op :ListObjectsV2
                                         :request {:Bucket bucket
                                                   :Prefix prefix}})]
          (when-let [objects (:Contents result)]
            (doseq [obj objects]
              (aws/invoke client {:op :DeleteObject
                                  :request {:Bucket bucket
                                            :Key (:Key obj)}}))
            (when (:IsTruncated result)
              (recur))))))))

(defn normalize-path [path]
  (when path
    (let [segments (-> path
                       (str/replace #"\\" "/")  ; Windows backslashes to forward
                       (str/replace #"^/" "")   ; Remove leading slash
                       (str/split #"/"))]
      (->> segments
           (reduce (fn [acc seg]
                     (cond
                       (= seg "..") (if (seq acc) (pop acc) acc)  ; Go up, but not above root
                       (or (= seg ".") (str/blank? seg)) acc      ; Skip . and empty
                       :else (conj acc seg)))                     ; Add normal segment
                   [])
           (str/join "/")))))

(defn resource-object-key [path]
  (str "resources/" (normalize-path path)))

(defn logo-object-key [filename]
  (str "site/logo/" filename))
