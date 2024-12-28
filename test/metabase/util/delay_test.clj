(ns ^:mb/once metabase.util.delay-test
  (:require [clojure.test :refer :all]
            [metabase.util.delay :as delay]))

(set! *warn-on-reflection* true)

(deftest delay-with-ttl-test
  (let [d (delay/delay-with-ttl 100 #(Object.))
        vals1 (doall (repeatedly 20 (fn [] @d)))
        _ (Thread/sleep 200)
        val2 @d]
    (is (apply = vals1))
    (is (not= val2 (first vals1)))))
