(ns markdownbrain.frontend-host-binding-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [markdownbrain.db :as db]
   [markdownbrain.middleware :as middleware]
   [markdownbrain.utils :as utils]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(defn setup-test-db [f]
  (let [temp-file (java.io.File/createTempFile "test-db" ".db")
        _ (.deleteOnExit temp-file)
        test-ds (delay (jdbc/get-datasource {:dbtype "sqlite" :dbname (.getPath temp-file)}))]
    (with-redefs [db/datasource test-ds]
      (db/init-db!)
      (f)
      (.delete temp-file))))

(use-fixtures :each setup-test-db)

(deftest test-frontend-host-binding-middleware
  (testing "Missing Host returns 400"
    (let [handler (middleware/wrap-frontend-host-binding (fn [_] {:status 200 :body "ok"}))
          resp (handler (assoc (mock/request :get "/") :headers {}))]
      (is (= 400 (:status resp)))))

  (testing "Invalid Host returns 400"
    (let [handler (middleware/wrap-frontend-host-binding (fn [_] {:status 200 :body "ok"}))
          req (-> (mock/request :get "/")
                  (assoc :headers {"host" "bad host"}))
          resp (handler req)]
      (is (= 400 (:status resp)))))

  (testing "Invalid Host port returns 400"
    (let [handler (middleware/wrap-frontend-host-binding (fn [_] {:status 200 :body "ok"}))
          req (-> (mock/request :get "/")
                  (assoc :headers {"host" "example.com:not-a-port"}))
          resp (handler req)]
      (is (= 400 (:status resp)))))

  (testing "Unbound Host returns 403"
    (let [handler (middleware/wrap-frontend-host-binding (fn [_] {:status 200 :body "ok"}))
          req (-> (mock/request :get "/")
                  (assoc :headers {"host" "unbound.example"}))
          resp (handler req)]
      (is (= 403 (:status resp)))))

  (testing "Bound Host passes vault context through"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "bound.example"
          _ (db/create-vault! vault-id tenant-id "Bound Site" domain (utils/generate-uuid))
          handler (middleware/wrap-frontend-host-binding
                   (fn [request]
                     {:status 200
                      :body {:domain (:markdownbrain/domain request)
                             :vault-id (get-in request [:markdownbrain/vault :id])}}))
          req (-> (mock/request :get "/")
                  (assoc :headers {"host" domain}))
          resp (handler req)]
      (is (= 200 (:status resp)))
      (is (= domain (get-in resp [:body :domain])))
      (is (= vault-id (get-in resp [:body :vault-id])))))

  (testing "Reserved paths bypass host binding"
    (let [handler (middleware/wrap-frontend-host-binding (fn [_] {:status 418 :body "teapot"}))
          resp (handler (mock/request :get "/console/init"))]
      (is (= 418 (:status resp))))))
