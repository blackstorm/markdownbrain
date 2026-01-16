(ns markdownbrain.response)

;; HTTP 响应辅助函数

(defn ok
  "返回 200 OK 响应"
  ([body]
   {:status 200
    :body body})
  ([body headers]
   {:status 200
    :headers headers
    :body body}))

(defn created
  "返回 201 Created 响应"
  [body]
  {:status 201
   :body body})

(defn bad-request
  "返回 400 Bad Request 响应"
  ([error-msg]
   {:status 400
    :body {:success false
           :error error-msg}})
  ([error-msg errors]
   {:status 400
    :body {:success false
           :error error-msg
           :errors errors}}))

(defn unauthorized
  "返回 401 Unauthorized 响应"
  [error-msg]
  {:status 401
   :body {:success false
          :error error-msg}})

(defn not-found
  "返回 404 Not Found 响应"
  [error-msg]
  {:status 404
   :body {:error error-msg}})

(defn success
  "返回成功响应，包含 success 字段"
  ([data]
   {:status 200
    :body (assoc data :success true)})
  ([data session]
   {:status 200
    :session session
    :body (assoc data :success true)}))

(defn html
  "返回 HTML 响应"
  [body]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body body})

(defn error
  [status error-msg]
  {:status status
   :body {:success false
          :error error-msg}})

(defn redirect
  "返回 302 重定向响应"
  [location]
  {:status 302
   :headers {"Location" location}
   :body ""})

(defn json-error
  "Return error response with JSON Content-Type header.
   Use this for API endpoints that return JSON errors."
  [status error-msg]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body {:success false
          :error error-msg}})
