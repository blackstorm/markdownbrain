(ns markdownbrain.lucide-icon-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [markdownbrain.lucide-icon :as lucide-icon]
   [selmer.parser :as selmer]))

(deftest lucide-icon-renders-inline-svg
  (lucide-icon/init!)
  (testing "direct icon name"
    (let [html (selmer/render "{{ lucide_icon(\"plus\") }}" {})]
      (is (re-find #"<svg" html))
      (is (re-find #"data-lucide=\"plus\"" html))
      (is (re-find #"lucide-plus" html))))

  (testing "adds classes"
    (let [html (selmer/render "{{ lucide_icon(\"plus\", \"icon-sm\") }}" {})]
      (is (re-find #"class=\"[^\"]*icon-sm" html))))

  (testing "supports variable icon name"
    (let [html (selmer/render "{{ lucide_icon(icon, \"icon-sm\") }}" {:icon "plus"})]
      (is (re-find #"data-lucide=\"plus\"" html))
      (is (re-find #"class=\"[^\"]*icon-sm" html))))

  (testing "legacy alias resolution"
    (let [html (selmer/render "{{ lucide_icon(\"check-circle\") }}" {})]
      (is (re-find #"<svg" html))
      (is (re-find #"data-lucide=\"check-circle\"" html))))

  (testing "invalid icon names are rejected"
    (is (= "" (selmer/render "{{ lucide_icon(\"../evil\") }}" {}))))

  (testing "aria label switches to role=img"
    (let [html (selmer/render "{{ lucide_icon(\"plus\", \"icon-sm\", \"Add\") }}" {})]
      (is (re-find #"role=\"img\"" html))
      (is (re-find #"aria-label=\"Add\"" html))
      (is (not (re-find #"aria-hidden=\"true\"" html))))))

