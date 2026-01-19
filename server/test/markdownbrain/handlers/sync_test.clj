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
                                     :assets [{:id asset-id :hash "md5-a"}]})
                      (assoc :path-params {:id note-id}))
          response (sync/sync-note request)
          refs (db/get-asset-refs-by-note vault-id note-id)]
      (is (= 200 (:status response)))
      (is (= 1 (count refs)))
      (is (= asset-id (:asset-client-id (first refs))))
      (is (empty? (get-in response [:body :need_upload_assets]))))))

(deftest test-sync-note-need-upload-assets
  (testing "returns missing assets when hashes differ or asset missing"
    (let [tenant-id (support/create-test-tenant!)
          {:keys [vault-id sync-key]} (support/create-test-vault! tenant-id "sync-assets-missing.com")
          note-id "note-asset-missing"
          asset-id "asset-keep"
          missing-id "asset-missing"
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id asset-id "img/a.png" "assets/a.png" 10 "image/png" "md5-a")
          request (-> (auth-request :post (str "/sync/notes/" note-id) sync-key
                                    {:path "notes/a.md"
                                     :content "Hello"
                                     :hash "hash-note"
                                     :assets [{:id asset-id :hash "md5-changed"}
                                              {:id missing-id :hash "md5-missing"}]})
                      (assoc :path-params {:id note-id}))
          response (sync/sync-note request)
          missing (get-in response [:body :need_upload_assets])]
      (is (= 200 (:status response)))
      (is (= [{:id asset-id :hash "md5-changed"}
              {:id missing-id :hash "md5-missing"}]
             missing)))))

(deftest test-sync-note-link-diff-no-change
  (testing "does not delete/insert links when only note content changes"
    (let [tenant-id (support/create-test-tenant!)
          {:keys [vault-id sync-key]} (support/create-test-vault! tenant-id "sync-links-no-change.com")
          note-a "note-a"
          note-b "note-b"
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "Note B.md" note-b "B" "{}" "hash-b" nil)
          request-1 (-> (auth-request :post (str "/sync/notes/" note-a) sync-key
                                      {:path "Note A.md"
                                       :content "Link to [[Note B]]"
                                       :hash "hash-a-1"
                                       :assets []})
                        (assoc :path-params {:id note-a}))
          _ (sync/sync-note request-1)
          delete-count (atom 0)
          insert-count (atom 0)
          original-delete db/delete-note-link-by-target!
          original-insert db/insert-note-link!]
      (with-redefs [db/delete-note-link-by-target! (fn [& args]
                                                     (swap! delete-count inc)
                                                     (apply original-delete args))
                    db/insert-note-link! (fn [& args]
                                           (swap! insert-count inc)
                                           (apply original-insert args))]
        (let [request-2 (-> (auth-request :post (str "/sync/notes/" note-a) sync-key
                                          {:path "Note A.md"
                                           :content "Updated text [[Note B]]"
                                           :hash "hash-a-2"
                                           :assets []})
                            (assoc :path-params {:id note-a}))
              response (sync/sync-note request-2)]
          (is (= 200 (:status response)))
          (is (= 0 @delete-count))
          (is (= 0 @insert-count)))))))

(deftest test-sync-note-link-diff-add-remove-update
  (testing "adds, removes, and updates links with minimal operations"
    (let [tenant-id (support/create-test-tenant!)
          {:keys [vault-id sync-key]} (support/create-test-vault! tenant-id "sync-links-diff.com")
          note-a "note-a"
          note-b "note-b"
          note-c "note-c"
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "Note B.md" note-b "B" "{}" "hash-b" nil)
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "Note C.md" note-c "C" "{}" "hash-c" nil)
          request-1 (-> (auth-request :post (str "/sync/notes/" note-a) sync-key
                                      {:path "Note A.md"
                                       :content "Link [[Note B]]"
                                       :hash "hash-a-1"
                                       :assets []})
                        (assoc :path-params {:id note-a}))
          _ (sync/sync-note request-1)
          delete-count (atom 0)
          insert-count (atom 0)
          original-delete db/delete-note-link-by-target!
          original-insert db/insert-note-link!]
      (with-redefs [db/delete-note-link-by-target! (fn [& args]
                                                     (swap! delete-count inc)
                                                     (apply original-delete args))
                    db/insert-note-link! (fn [& args]
                                           (swap! insert-count inc)
                                           (apply original-insert args))]
        (let [request-2 (-> (auth-request :post (str "/sync/notes/" note-a) sync-key
                                          {:path "Note A.md"
                                           :content "Now [[Note C]] only"
                                           :hash "hash-a-2"
                                           :assets []})
                            (assoc :path-params {:id note-a}))
              response-2 (sync/sync-note request-2)]
          (is (= 200 (:status response-2)))
          (is (= 1 @delete-count))
          (is (= 1 @insert-count))
          (reset! delete-count 0)
          (reset! insert-count 0))
        (let [request-3 (-> (auth-request :post (str "/sync/notes/" note-a) sync-key
                                          {:path "Note A.md"
                                           :content "Alias [[Note C|My C]]"
                                           :hash "hash-a-3"
                                           :assets []})
                            (assoc :path-params {:id note-a}))
              response-3 (sync/sync-note request-3)]
          (is (= 200 (:status response-3)))
          (is (= 1 @delete-count))
          (is (= 1 @insert-count)))))))

(deftest test-sync-note-asset-ref-removal
  (testing "removes asset ref when all notes drop the asset"
    (with-redefs [object-store/delete-object! (fn [_ _] nil)]
      (let [tenant-id (support/create-test-tenant!)
            {:keys [vault-id sync-key]} (support/create-test-vault! tenant-id "sync-asset-refs.com")
            asset-id "asset-shared"
            _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id asset-id "assets/shared.png" "assets/shared.png" 10 "image/png" "md5-shared")
            note-a "note-a"
            note-b "note-b"
            request-a (-> (auth-request :post (str "/sync/notes/" note-a) sync-key
                                        {:path "A.md"
                                         :content "A"
                                         :hash "hash-a"
                                         :assets [{:id asset-id :hash "md5-shared"}]})
                          (assoc :path-params {:id note-a}))
            request-b (-> (auth-request :post (str "/sync/notes/" note-b) sync-key
                                        {:path "B.md"
                                         :content "B"
                                         :hash "hash-b"
                                         :assets [{:id asset-id :hash "md5-shared"}]})
                          (assoc :path-params {:id note-b}))]
        (sync/sync-note request-a)
        (sync/sync-note request-b)

        (let [remove-a (-> (auth-request :post (str "/sync/notes/" note-a) sync-key
                                         {:path "A.md"
                                          :content "A no asset"
                                          :hash "hash-a-2"
                                          :assets []})
                           (assoc :path-params {:id note-a}))]
          (sync/sync-note remove-a)
          (is (some? (db/get-asset-by-client-id vault-id asset-id))))

        (let [remove-b (-> (auth-request :post (str "/sync/notes/" note-b) sync-key
                                         {:path "B.md"
                                          :content "B no asset"
                                          :hash "hash-b-2"
                                          :assets []})
                           (assoc :path-params {:id note-b}))]
          (sync/sync-note remove-b)
          (is (nil? (db/get-asset-by-client-id vault-id asset-id))))))))

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

(deftest test-sync-asset-dedup
  (testing "skips upload when asset hash matches"
    (let [tenant-id (support/create-test-tenant!)
          {:keys [vault-id sync-key]} (support/create-test-vault! tenant-id "sync-dedup.com")
          asset-id "asset-dup"
          content "hello"
          base64-content (.encodeToString (java.util.Base64/getEncoder) (.getBytes content "UTF-8"))
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id asset-id "assets/a.png" "assets/a.png" 10 "image/png" "md5-same")
          stored (atom 0)
          request (-> (auth-request :post (str "/sync/assets/" asset-id) sync-key
                                    {:path "assets/a.png"
                                     :contentType "image/png"
                                     :size 10
                                     :hash "md5-same"
                                     :content base64-content})
                      (assoc :path-params {:id asset-id}))]
      (with-redefs [object-store/put-object! (fn [_ _ _ _] (swap! stored inc))]
        (let [response (sync/sync-asset request)]
          (is (= 200 (:status response)))
          (is (= "skipped" (get-in response [:body :status])))
          (is (= 0 @stored))))))

  (testing "stores upload when asset hash changes"
    (let [tenant-id (support/create-test-tenant!)
          {:keys [vault-id sync-key]} (support/create-test-vault! tenant-id "sync-dedup-2.com")
          asset-id "asset-dup-2"
          content "new"
          base64-content (.encodeToString (java.util.Base64/getEncoder) (.getBytes content "UTF-8"))
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id asset-id "assets/a.png" "assets/a.png" 10 "image/png" "md5-old")
          stored (atom 0)
          request (-> (auth-request :post (str "/sync/assets/" asset-id) sync-key
                                    {:path "assets/a.png"
                                     :contentType "image/png"
                                     :size 10
                                     :hash "md5-new"
                                     :content base64-content})
                      (assoc :path-params {:id asset-id}))]
      (with-redefs [object-store/put-object! (fn [_ _ _ _] (swap! stored inc))]
        (let [response (sync/sync-asset request)]
          (is (= 200 (:status response)))
          (is (= "stored" (get-in response [:body :status])))
          (is (= 1 @stored)))))))
