(ns markdownbrain.handlers.admin.test-utils
  "Shared test utilities for admin handler tests."
  (:require
   [clojure.java.io :as io]
   [markdownbrain.db :as db]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(def test-db-file (atom nil))

(defn setup-test-db
  "Test fixture that sets up a temporary SQLite database for each test."
  [f]
  (let [temp-file (java.io.File/createTempFile "test-db" ".db")
        _ (.deleteOnExit temp-file)
        db-path (.getPath temp-file)
        test-ds (delay (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path "?foreign_keys=ON")}))]
    (reset! test-db-file temp-file)
    (with-redefs [db/datasource test-ds]
      (db/init-db!)
      (f)
      (.delete temp-file))))

(defn authenticated-request
  "Create a mock request with session authentication."
  [method uri tenant-id user-id & {:keys [body]}]
  (let [req (mock/request method uri)]
    (cond-> req
      true (assoc :session {:tenant-id tenant-id :user-id user-id})
      body (assoc :body-params body))))

(defn create-test-png
  "Create a test PNG image with specified dimensions."
  [width height]
  (let [image (java.awt.image.BufferedImage. width height java.awt.image.BufferedImage/TYPE_INT_RGB)
        g2d (.createGraphics image)
        baos (java.io.ByteArrayOutputStream.)]
    (.setColor g2d java.awt.Color/BLUE)
    (.fillRect g2d 0 0 width height)
    (.dispose g2d)
    (javax.imageio.ImageIO/write image "png" baos)
    (.toByteArray baos)))

(defn create-temp-file
  "Create a temp file map mimicking ring multipart upload.
   Actually writes bytes to the tempfile so it can be read by handlers."
  [bytes content-type]
  (let [f (java.io.File/createTempFile "test" ".png")]
    (.deleteOnExit f)
    (with-open [out (io/output-stream f)]
      (.write out ^bytes bytes))
    {:tempfile f
     :content-type content-type
     :size (count bytes)
     :filename "test.png"}))

(defn bytes=
  "Compare two byte arrays for equality."
  [^bytes a ^bytes b]
  (java.util.Arrays/equals a b))
