(ns electronicsmech.governor
  "ElectronicsMechGovernor — the independent safety/scope layer gating
  every service scheduling/logistics proposal an advisor may make for
  an electronics mechanics and servicers crew. The governor never
  dispatches hardware itself, never performs electronics-repair work,
  and never finalizes an electronics-repair-execution decision (e.g.
  deciding to proceed with a specific electronics repair) or
  overrides/bypasses a shop-safety-officer's judgment — those are
  permanently out of this actor's scope and remain the shop safety
  officer's exclusive judgment (README's 'Robotics premise': this actor
  coordinates SERVICE SCHEDULING/LOGISTICS ONLY — it never performs
  electronics-repair work itself). Modeled on cloud-itonami-isco-7127's
  hvacmech.governor (closest domain shape, itself modeled on
  cloud-itonami-isco-7111's housebuilder.governor and
  cloud-itonami-isco-3313's accountingsupport.governor /
  cloud-itonami-isco-9311's mininglabor.governor for the
  physical-safety-domain shape).

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. technician provenance — the technician must be independently
                                verified/registered (including
                                certification status) before any
                                action.
    2. service-account provenance — the service account must be
                                independently verified/registered
                                before any action.
    3. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never performs electronics-repair work
                                itself; it only gates what the advisor
                                may coordinate).
    4. closed op-allowlist    — only :log-service-record,
                                :schedule-service-operation,
                                :flag-safety-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action  — any proposal to directly finalize an
                                electronics-repair-execution decision
                                (e.g. deciding to proceed with a
                                specific electronics repair), or to
                                override or bypass a
                                shop-safety-officer's judgment, is a
                                hard, permanent block (checked both
                                against the proposed :op and,
                                defense-in-depth, against the
                                proposal's :rationale text — matched as
                                full finalization/execution ACTION
                                phrases such as \"proceed with the
                                electronics repair\" / \"authorize the
                                electronics-repair execution\" /
                                \"override the shop-safety officer's
                                judgment\", never as bare nouns like
                                \"electronics\", \"repair\" or \"shop
                                safety officer\", so the check can never
                                self-trip on the advisor's own routine
                                rationale text, e.g. \"logged service
                                record for technician …\" or \"scheduled
                                service operation for electronics
                                diagnostic task …\" or \"…routed for shop
                                safety officer review\" — all three
                                legitimately contain those bare nouns
                                but none is a finalization action, and
                                all are exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-safety-concern (a capacitor-discharge-shock /
                                solder-fume-exposure / electrical-hazard
                                concern always escalates to a human,
                                never auto-commits).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [electronicsmech.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 2000)

(def allowed-ops
  #{:log-service-record :schedule-service-operation
    :flag-safety-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops`
;; above, so they are already refused by the closed-allowlist check
;; below; they are named again here — as explicit finalization/
;; execution ACTIONS, never bare nouns — so a future allowlist edit
;; cannot silently re-open this specific out-of-scope path without
;; also touching this list.
(def ^:private scope-excluded-ops
  #{:finalize-electronics-repair-decision
    :authorize-electronics-repair-execution
    :proceed-with-electronics-repair
    :override-shop-safety-officer-judgment
    :bypass-shop-safety-officer-judgment})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("electronics", "repair", "shop", "safety", "officer", "capacitor",
;; "solder") — so this can never match inside the mock advisor's own
;; default rationale text (which legitimately contains those bare
;; nouns, e.g. "electronics diagnostic task" / "shop safety officer
;; review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["proceed with the electronics repair"
   "proceed with the electronics-repair"
   "finalize the electronics-repair execution decision"
   "finalize the electronics repair execution decision"
   "authorize the electronics-repair execution"
   "authorize the electronics repair execution"
   "override the shop-safety officer's judgment"
   "override the shop safety officer's judgment"
   "bypass the shop-safety officer's judgment"
   "bypass the shop safety officer's judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower-case (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal technician-record service-account-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? technician-record)
      (conj {:rule :no-technician
             :detail "未登録 technician への提案は不可（technician record は独立して検証・登録済み — certification status を含む — でなければならない）"})

      (nil? service-account-record)
      (conj {:rule :no-service-account
             :detail "未登録 service account への提案は不可（service account record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は electronics-repair work を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "electronics-repair の実行判断の確定・shop-safety-officer の判断の上書き/回避は、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `electronicsmech.store/Store`. Pure — never
  mutates the store, never dispatches a service operation."
  [request _context proposal store]
  (let [technician-record (store/technician store (:technician-id request))
        service-account-record (some->> (:service-account-id proposal) (store/service-account store))
        hard (hard-violations proposal technician-record service-account-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-safety-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
