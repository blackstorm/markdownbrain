(ns markdownbrain.handlers.sync-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.handlers.sync :as sync]
            [markdownbrain.db :as db]
            [markdownbrain.test-support :as support]
            [markdownbrain.object-store :as object-store]
            [markdownbrain.utils :as utils]
            [ring.mock.request :as mock]))

(use-fixtures :each (support/create-temp-db-fixture))

(defn- auth-request
  [method uri sync-key body]
  (cond-> (mock/request method uri)
    true (assoc :headers {"authorization" (str "Bearer " sync-key)})
    body (assoc :body-params body)))

(deftest test-sync-changes
  (testing "returns need_upsert and deletes missing files"
    (with-redefs [object-store/delete-object! (fn [_ _] nil)]
      (let [tenant-id (support/create-test-tenant!)
            {:keys [vault-id sync-key]} (support/create-test-vault! tenant-id "sync.com")
            note-id-1 "note-1"
            note-id-2 "note-2"
            note-id-3 "note-3"
            asset-id-1 "asset-1"
            asset-id-2 "asset-2"
            _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "a.md" note-id-1 "A" "{}" "hash-a" nil)
            _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "b.md" note-id-2 "B" "{}" "hash-b" nil)
            _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "c.md" note-id-3 "C" "{}" "hash-c" nil)
            _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id asset-id-1 "img/a.png" "assets/a.png" 10 "image/png" "md5-a")
            _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id asset-id-2 "img/b.png" "assets/b.png" 20 "image/png" "md5-b")
            request (auth-request :post "/sync/changes" sync-key
                                  {:notes [{:id note-id-1 :hash "hash-a"}
                                           {:id note-id-2 :hash "hash-b-new"}]
                                   :assets [{:id asset-id-1 :hash "md5-a"}
                                            {:id "asset-3" :hash "md5-c"}]})
            response (sync/sync-changes request)
            body (:body response)]
        (is (= 200 (:status response)))
        (is (= [{:id note-id-2 :hash "hash-b-new"}] (get-in body [:need_upsert :notes])))
        (is (= [{:id "asset-3" :hash "md5-c"}] (get-in body [:need_upsert :assets])))
        (is (= [{:id note-id-3 :hash "hash-c"}] (get-in body [:deleted_on_server :notes])))
        (is (= [{:id asset-id-2 :hash "md5-b"}] (get-in body [:deleted_on_server :assets])))
        (is (nil? (db/get-note-by-client-id vault-id note-id-3)))
        (is (nil? (db/get-asset-by-client-id vault-id asset-id-2)))))))

(deftest test-sync-note-upsert-with-asset-refs
  (testing "upsert note stores content and updates asset refs"
    (let [tenant-id (support/create-test-tenant!)
          {:keys [vault-id sync-key]} (support/create-test-vault! tenant-id "sync-assets.com")
          note-id "note-asset"
          asset-id "asset-123"
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id asset-id "img/a.png" "assets/a.png" 10 "image/png" "md5-a")
          request (-> (auth-request :post (str "/sync/notes/" note-id) sync-key
                                    {:path "notes/a.md"
                                     :content "Hello"
                                     :hash "hash-note"
                                     :assetIds [asset-id]})
                      (assoc :path-params {:id note-id}))
          response (sync/sync-note request)
          refs (db/get-asset-refs-by-note vault-id note-id)]
      (is (= 200 (:status response)))
      (is (= 1 (count refs)))
      (is (= asset-id (:asset-client-id (first refs)))))))

(deftest test-delete-note-asset
  (testing "removes ref and deletes asset when no refs remain"
    (with-redefs [object-store/delete-object! (fn [_ _] nil)]
      (let [tenant-id (support/create-test-tenant!)
            {:keys [vault-id sync-key]} (support/create-test-vault! tenant-id "sync-delete.com")
            note-id "note-x"
            asset-id "asset-x"
            _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id asset-id "img/x.png" "assets/x.png" 10 "image/png" "md5-x")
            _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "x.md" note-id "X" "{}" "hash-x" nil)
            _ (db/upsert-note-asset-ref! vault-id note-id asset-id)
            request (-> (auth-request :delete (str "/sync/notes/" note-id "/assets/" asset-id) sync-key nil)
                        (assoc :path-params {:note_id note-id :asset_id asset-id}))
            response (sync/delete-note-asset request)]
        (is (= 200 (:status response)))
        (is (empty? (db/get-asset-refs-by-note vault-id note-id)))
        (is (nil? (db/get-asset-by-client-id vault-id asset-id)))))))
