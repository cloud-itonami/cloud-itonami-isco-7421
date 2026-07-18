(ns electronicsmech.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [electronicsmech.actor :as actor]
            [electronicsmech.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-technician! st {:technician-id "tech-1" :name "Kobo Yamada" :certified? true})
    (store/register-service-account! st {:service-account-id "SA-1" :name "Kobo Electronics Repair" :max-supply-cost 2000})
    st))

(deftest commits-a-registered-service-log
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:technician-id "tech-1" :op :log-service-record :stake :low
                  :service-account-id "SA-1" :task "circuit-board diagnostic log"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "tech-1"))))))

(deftest holds-an-unregistered-service-account-proposal
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:technician-id "tech-1" :op :log-service-record :stake :low
                  :service-account-id "SA-ghost" :task "circuit-board diagnostic log"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "tech-1")))))

(deftest interrupts-then-approves-safety-concern-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:technician-id "tech-1" :op :flag-safety-concern :stake :low
                  :service-account-id "SA-1" :hazard-type :capacitor-discharge-shock}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "tech-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "tech-1")))))))

(deftest holds-a-scope-excluded-op-even-at-high-confidence
  (testing "an actor run can never commit a proposal that would finalize an electronics-repair-execution decision, regardless of disposition path"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:technician-id "tech-1" :op :finalize-electronics-repair-decision :stake :low
                    :service-account-id "SA-1" :task "electronics repair decision"}
          result (actor/run-request! graph request {} "thread-4")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "tech-1"))))))
