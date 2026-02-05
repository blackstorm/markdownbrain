(ns mdbrain.object-store.s3
  "S3-compatible object storage implementation."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [mdbrain.config :as config]
            [mdbrain.object-store :as store]
            [cognitect.aws.client.api :as aws]
            [cognitect.aws.credentials :as creds]))

;; ============================================================
;; S3 Client Management
;; ============================================================

(defn parse-endpoint
  "Parse endpoint into protocol/hostname/port components.

   - If the scheme is missing, defaults to http://
   - If the port is missing:
     - https defaults to 443
     - http defaults to 9000 (common S3-compatible default)

   Example:
   - 'http://localhost:9000' -> {:protocol :http :hostname \"localhost\" :port 9000}"
  [endpoint]
  (let [raw (-> (str endpoint) str/trim)
        with-scheme (if (re-find #"^https?://" raw)
                      raw
                      (str "http://" raw))
        uri (java.net.URI. with-scheme)
        scheme (or (.getScheme uri) "http")
        hostname (.getHost uri)
        port (.getPort uri)
        protocol (keyword scheme)
        port (if (pos? port)
               port
               (if (= scheme "https") 443 9000))]
    {:protocol protocol
     :hostname hostname
     :port port}))

(defn- create-s3-client
  "Create an S3 client using configuration."
  []
  (let [{:keys [endpoint access-key secret-key region]} (config/s3-config)]
    (when (and endpoint access-key secret-key)
      (let [{:keys [protocol hostname port]} (parse-endpoint endpoint)]
        (aws/client {:api :s3
                     :region region
                     :endpoint-override {:protocol protocol
                                         :hostname hostname
                                         :port port}
                     :credentials-provider (creds/basic-credentials-provider
                                             {:access-key-id access-key
                                              :secret-access-key secret-key})})))))

(defn- bucket-name []
  (:bucket (config/s3-config)))

(defn- bucket-exists? [client]
  (let [bucket (bucket-name)
        result (aws/invoke client {:op :HeadBucket
                                   :request {:Bucket bucket}})]
    (if (= :cognitect.anomalies/unavailable (:cognitect.anomalies/category result))
      (throw (ex-info "S3 connection failed" {:endpoint (:endpoint (config/s3-config))
                                               :error (:cognitect.anomalies/message result)}))
      (not (:cognitect.anomalies/category result)))))

(defn- create-bucket! [client]
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
      (log/info "Created S3 bucket:" bucket))))

(defn- ensure-bucket! [client]
  "Ensure the S3 bucket exists."
  (if (bucket-exists? client)
    (log/info "S3 bucket exists:" (bucket-name))
    (create-bucket! client)))

;; ============================================================
;; S3ObjectStore Implementation
;; ============================================================

(defrecord S3ObjectStore [client]
  store/ObjectStore
  
  (put-object!* [_ vault-id object-key content content-type]
    (let [bucket (bucket-name)
          full-key (str (store/vault-prefix vault-id) object-key)
          body (if (bytes? content) content (.getBytes content "UTF-8"))]
      (let [result (aws/invoke client {:op :PutObject
                                       :request {:Bucket bucket
                                                 :Key full-key
                                                 :Body body
                                                 :ContentType content-type}})]
        (if (:cognitect.anomalies/category result)
          (do (log/error "Failed to put object:" result) nil)
          (do (log/debug "Put object:" bucket full-key) result)))))
  
  (get-object* [_ vault-id object-key]
    (let [bucket (bucket-name)
          full-key (str (store/vault-prefix vault-id) object-key)
          result (aws/invoke client {:op :GetObject
                                     :request {:Bucket bucket
                                               :Key full-key}})]
      (if (:cognitect.anomalies/category result)
        (do (log/debug "Object not found:" bucket full-key) nil)
        result)))
  
  (delete-object!* [_ vault-id object-key]
    (let [bucket (bucket-name)
          full-key (str (store/vault-prefix vault-id) object-key)
          result (aws/invoke client {:op :DeleteObject
                                     :request {:Bucket bucket
                                               :Key full-key}})]
      (if (:cognitect.anomalies/category result)
        (do (log/error "Failed to delete object:" result) nil)
        (do (log/debug "Deleted object:" bucket full-key) result))))
  
  (head-object* [_ vault-id object-key]
    (let [bucket (bucket-name)
          full-key (str (store/vault-prefix vault-id) object-key)
          result (aws/invoke client {:op :HeadObject
                                     :request {:Bucket bucket
                                               :Key full-key}})]
      (if (:cognitect.anomalies/category result)
        nil
        result)))
  
  (delete-vault-objects!* [_ vault-id]
    (let [bucket (bucket-name)
          prefix (store/vault-prefix vault-id)]
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
              (recur)))))))
  
  (public-url* [_ vault-id object-key]
    (when-let [public-url (:public-url (config/s3-config))]
      (let [bucket (bucket-name)
            full-key (str (store/vault-prefix vault-id) object-key)]
        (str (str/replace public-url #"/$" "") "/" bucket "/" full-key)))))

;; ============================================================
;; Factory Function
;; ============================================================

(defn create-s3-store
  "Create and initialize an S3ObjectStore.
   Validates configuration and ensures bucket exists."
  []
  (log/info "Creating S3 storage backend...")
  (let [{:keys [endpoint]} (config/s3-config)]
    (when-not endpoint
      (throw (ex-info "S3_ENDPOINT is required for S3 storage"
                      {:type :configuration-error})))
    (let [client (create-s3-client)]
      (when-not client
        (throw (ex-info "Failed to create S3 client. Check S3 configuration."
                        {:type :configuration-error})))
      (ensure-bucket! client)
      (log/info "S3 storage ready - endpoint:" endpoint "bucket:" (bucket-name))
      (->S3ObjectStore client))))
