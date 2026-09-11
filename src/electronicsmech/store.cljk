(ns electronicsmech.store
  "SSoT for the ISCO-08 7421 electronics-repair service scheduling/logistics
  coordination actor (itonami actor pattern, ADR-2607121000 / CLAUDE.md
  Actors section; README's 'Robotics premise' — a service
  scheduling/logistics coordination robot performs service-call
  scheduling, service-call/parts-usage/diagnostic-status record logging
  and electronics-parts supply-order coordination for an electronics
  mechanics and servicers crew under this advisor/governor pair, which
  never dispatches hardware itself, never performs electronics-repair
  work itself, and never finalizes an electronics-repair-execution
  decision or overrides a shop-safety-officer's judgment — those remain
  the shop safety officer's exclusive judgment). Modeled on
  cloud-itonami-isco-7127's hvacmech.store (closest domain shape, itself
  modeled on cloud-itonami-isco-7111's housebuilder.store and
  cloud-itonami-isco-3313's accountingsupport.store /
  cloud-itonami-isco-9311's mininglabor.store for the
  physical-safety-domain shape).

  Domain:

    technician      — a registered electronics technician
                       {:technician-id :name :certified?}. `:certified?`
                       is informational registered data (the technician's
                       certification status as recorded at registration
                       time) — the governor's provenance check only
                       requires the technician record to exist
                       (independently verified/registered before any
                       action); it never lets a proposal override or
                       bypass a shop-safety-officer's judgment itself
                       (see electronicsmech.governor's
                       scope-excluded-action rule).
    service-account — a registered service account/site {:service-account-id
                       :name :max-supply-cost number}. `:max-supply-cost`
                       is an informational registered ceiling used only to
                       decide whether a `:coordinate-supply-order`
                       proposal escalates to human sign-off (the governor
                       never blocks a within-threshold order outright; it
                       only decides commit vs. escalate).
    record           — a committed operating record (a logged
                       service-call/parts-usage/diagnostic-status entry, a
                       scheduled service operation, a flagged safety
                       concern, or a coordinated supply order) — written
                       ONLY via commit-record!.
    ledger           — append-only audit trail, commit or hold.")

(defprotocol Store
  (technician [s technician-id])
  (service-account [s service-account-id])
  (records-of [s technician-id])
  (ledger [s])
  (register-technician! [s tech])
  (register-service-account! [s account])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (technician [_ technician-id] (get-in @a [:technicians technician-id]))
  (service-account [_ service-account-id] (get-in @a [:service-accounts service-account-id]))
  (records-of [_ technician-id] (filter #(= technician-id (:technician-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-technician! [s tech]
    (swap! a assoc-in [:technicians (:technician-id tech)] tech) s)
  (register-service-account! [s account]
    (swap! a assoc-in [:service-accounts (:service-account-id account)] account) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:technicians {} :service-accounts {} :records [] :ledger []}
                                    seed)))))
