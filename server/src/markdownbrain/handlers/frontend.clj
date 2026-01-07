(ns markdownbrain.handlers.frontend
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [markdownbrain.db :as db]
   [markdownbrain.markdown :as md]
   [markdownbrain.response :as resp]
   [selmer.parser :as selmer]))

(defn get-current-vault
  [request]
  (when-let [host (get-in request [:headers "host"])]
    (let [domain (first (str/split host #":"))]
      (db/get-vault-by-domain domain))))

(defn parse-path-ids
  [path]
  (if (or (nil? path) (= path "/") (= path ""))
    []
    (-> path
        (str/replace #"^/" "")
        (str/split #"\+")
        vec)))

(defn build-push-url
  [current-url from-note-id target-note-id root-note-id]
  (let [current-path (if current-url
                       (-> current-url
                           (str/replace #"^https?://[^/]+" "")
                           (str/replace #"\?.*$" ""))
                       "/")]
    (cond
      (= current-path "/")
      (if root-note-id
        (str "/" root-note-id "+" target-note-id)
        (str "/" target-note-id))
      
      :else
      (let [path-parts (-> current-path
                           (str/replace #"^/" "")
                           (str/split #"\+"))]
        (if from-note-id
          (let [idx (.indexOf (vec path-parts) from-note-id)
                kept-parts (if (>= idx 0)
                             (take (inc idx) path-parts)
                             path-parts)]
            (str "/" (str/join "+" (concat kept-parts [target-note-id]))))
          (str "/" (str/join "+" (concat path-parts [target-note-id]))))))))

(defn- prepare-note-data
  [note vault-id]
  (let [links (db/get-note-links vault-id (:client-id note))
        html-content (md/render-markdown (:content note) vault-id links)
        title (or (md/extract-title (:content note))
                  (-> (:path note)
                      (str/replace #"\.md$" "")
                      (str/replace #"/" " / ")))
        backlinks (db/get-backlinks-with-notes vault-id (:client-id note))
        backlinks-with-meta (mapv (fn [backlink]
                                    (assoc backlink
                                           :title (or (md/extract-title (:content backlink))
                                                      (str/replace (:path backlink) #"\.md$" ""))
                                           :description (md/extract-description (:content backlink))))
                                  backlinks)]
    {:note {:client-id (:client-id note)
            :title title
            :html-content html-content
            :path (:path note)
            :updated-at (:updated-at note)}
     :backlinks backlinks-with-meta}))

(defn get-note-fragment
  [request]
  (let [client-id (get-in request [:path-params :id])
        vault (get-current-vault request)]

    (if-not vault
      {:status 404 :body "Site not found"}

      (let [vault-id (:id vault)]
        (if-let [note (db/get-note-for-frontend vault-id client-id)]
          (let [render-data (prepare-note-data note vault-id)
                
                from-note-id (get-in request [:headers "x-from-note-id"])
                current-url (get-in request [:headers "hx-current-url"])
                root-note-id (:root-note-id vault)
                
                push-url (build-push-url current-url from-note-id client-id root-note-id)
                
                html-body (selmer/render-file "templates/frontend/note.html" render-data)]
            
            {:status 200
             :headers {"Content-Type" "text/html; charset=utf-8"
                       "HX-Push-Url" push-url}
             :body html-body})
          {:status 404 :body "Note not found"})))))

(defn get-note
  [request]
  (let [path (get-in request [:path-params :path] "/")
        path-client-ids (parse-path-ids path)
        vault (get-current-vault request)
        is-htmx? (get-in request [:headers "hx-request"])]

    (if-not vault
      {:status 404 :body "Site not found"}

      (let [vault-id (:id vault)]
          (if (empty? path-client-ids)
          (if-let [root-client-id (:root-note-id vault)]
            (if-let [root-note (db/get-note-for-frontend vault-id root-client-id)]
              (let [render-data (prepare-note-data root-note vault-id)
                    description (md/extract-description (:content root-note) 160)]
                (if is-htmx?
                  {:status 200
                   :headers {"Content-Type" "text/html; charset=utf-8"}
                   :body (selmer/render-file "templates/frontend/note.html" render-data)}
                  (resp/html (selmer/render-file "templates/frontend/note-page.html"
                                                  {:notes [render-data]
                                                   :vault vault
                                                   :description description}))))
              (let [notes (db/list-notes-by-vault vault-id)]
                (resp/html (selmer/render-file "templates/frontend/home.html"
                                                {:vault vault
                                                 :notes notes}))))
            (let [notes (db/list-notes-by-vault vault-id)]
              (resp/html (selmer/render-file "templates/frontend/home.html"
                                              {:vault vault
                                               :notes notes}))))

          (let [valid-notes (keep #(db/get-note-for-frontend vault-id %) path-client-ids)
                valid-client-ids (mapv :client-id valid-notes)
                
                needs-correction? (not= (count valid-client-ids) (count path-client-ids))
                corrected-path (when needs-correction?
                                 (if (empty? valid-client-ids)
                                   "/"
                                   (str "/" (str/join "+" valid-client-ids))))]

            (cond
              (empty? valid-notes)
              {:status 404 :body "Note not found"}

              is-htmx?
              (let [last-note (last valid-notes)
                    render-data (prepare-note-data last-note vault-id)
                    
                    from-note-id (get-in request [:headers "x-from-note-id"])
                    current-url (get-in request [:headers "hx-current-url"])
                    root-note-id (:root-note-id vault)
                    push-url (or corrected-path
                                 (build-push-url current-url from-note-id (:client-id last-note) root-note-id))]
                
                {:status 200
                 :headers {"Content-Type" "text/html; charset=utf-8"
                           "HX-Push-Url" push-url}
                 :body (selmer/render-file "templates/frontend/note.html" render-data)})

              :else
              (let [notes-data (mapv #(prepare-note-data % vault-id) valid-notes)
                    first-note (first valid-notes)
                    description (md/extract-description (:content first-note) 160)
                    response-body (selmer/render-file "templates/frontend/note-page.html"
                                                      {:notes notes-data
                                                       :vault vault
                                                       :description description})]
                (if needs-correction?
                  {:status 200
                   :headers {"Content-Type" "text/html; charset=utf-8"
                             "HX-Replace-Url" corrected-path}
                   :body response-body}
                  (resp/html response-body))))))))))
