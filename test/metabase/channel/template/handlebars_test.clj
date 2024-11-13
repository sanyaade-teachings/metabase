(ns metabase.channel.template.handlebars-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [metabase.channel.template.handlebars :as handlebars]
   [metabase.util :as u]
   [metabase.util.files :as u.files]
   [metabase.util.random :as u.random])
  (:import
   (com.github.jknack.handlebars
    Parser Helper Template)
   (com.github.jknack.handlebars.io
    TemplateSource
    ClassPathTemplateLoader)))


(set! *warn-on-reflection* true)

(def custom-hbs
  (doto (handlebars/registry (handlebars/classpath-loader "/" ""))
    (.registerHelper "uppercase" (reify Helper
                                   (apply [_this arg _options]
                                     (u/upper-case-en arg))))))

(defn do-with-temp-template!
  [filename content thunk]
  ;; create the channel_template folder if not exists
  (let [filename  (format "channel_template/%s" filename)
        temp-file (format "test_resources/%s" filename)]
    (try
      (spit temp-file content)
      (thunk filename)
      (finally
        (io/delete-file temp-file)))))

(defmacro with-temp-template!
  [[filename-binding filename content] & body]
  `(do-with-temp-template! ~filename ~content (fn [~filename-binding] ~@body)))

(deftest render-string-test
  (testing "Render a template string with a context."
    (are [expected template context]
         (= expected (handlebars/render-string template context))

      "Hello Ngoc" "Hello {{name}}"                                 {:name "Ngoc"}
      "Hello Ngoc" "Hello {{name}}"                                 {"name" "Ngoc"}
      "Hello Ngoc" "Hello {{who.name}}"                             {:who {:name "Ngoc"}}
      "Hello "     "Hello {{#unless hide_name}}{{name}}{{/unless}}" {:name "Ngoc" :hide_name true}
      "" "" {})

    (testing "with custom reqistry"
      (is (= "NGOC" (handlebars/render-string custom-hbs "{{uppercase name}}" {:name "Ngoc"}))))))

(deftest render-test
  (testing "Render a template with a context."
    (with-temp-template! [tmpl-name "tmpl.hbs" "Hello {{name}}"]
      (is (= "Hello Ngoc" (handlebars/render tmpl-name {:name "Ngoc"}))))
    (with-temp-template! [tmpl-name "tmpl.handlebars" "Hello {{name}}"]
      (is (= "Hello Ngoc" (handlebars/render tmpl-name {:name "Ngoc"})))))

  (testing "with custom req"
    (with-temp-template! [tmpl-name "tmpl.hbs" "Hello {{uppercase name}}"]
      (is (= "Hello NGOC" (handlebars/render custom-hbs tmpl-name {:name "Ngoc"}))))))

(deftest reload-template-if-it's-changed
  (testing "reload template if it's changed"
    (with-temp-template! [tmpl-name (u.random/random-name) "Hello {{name}}"]
      (is (= "Hello Ngoc" (handlebars/render tmpl-name {:name "Ngoc"})))
      (spit (format "test_resources/%s" tmpl-name) "Hello {{name}} updated!")
      (is (= "Hello Ngoc updated!" (handlebars/render tmpl-name {:name "Ngoc"}))))))

#_(deftest atom-template-cache-test
    (binding [*warn-on-reflection* false]
      (let [the-cache (fn [cache]
                        (.cache cache))
            parser    (reify Parser
                        (parse [_this source]
                          (reify Template
                            (^String apply [_this _context]  ; Removed type hints that were causing issues
                              (format "parsed %s" source)))))
            get-cache (fn [cache source]
                        (.get cache source parser))
            template-source (fn [source]
                              (reify TemplateSource
                                (content [_ _] (format "content %s" source))
                                (filename [_] source)
                                (lastModified [_] 0)))]
        (testing "reload=false should not reload template"
          (let [cache (#'handlebars/make-atom-template-cache false)]
            (is (= "parsed source" (get-cache cache (template-source "source")))))))))
