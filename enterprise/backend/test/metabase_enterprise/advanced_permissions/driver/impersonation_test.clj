(ns ^:mb/driver-tests metabase-enterprise.advanced-permissions.driver.impersonation-test
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [metabase-enterprise.advanced-permissions.api.util-test :as advanced-perms.api.tu]
   [metabase-enterprise.advanced-permissions.driver.impersonation :as impersonation]
   [metabase-enterprise.test :as met]
   [metabase.driver.postgres-test :as postgres-test]
   [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
   [metabase.query-processor :as qp]
   [metabase.request.core :as request]
   [metabase.sync.core :as sync]
   [metabase.test :as mt]
   [metabase.test.data.sql :as sql.tx]
   [metabase.util :as u]
   [toucan2.core :as t2]))

(deftest ^:parallel connection-impersonation-role-test
  (testing "Returns nil when no impersonations are in effect"
    (mt/with-test-user :rasta
      (is (nil? (@#'impersonation/connection-impersonation-role (mt/db)))))))

(deftest connection-impersonation-role-test-2
  (testing "Correctly fetches the impersonation when one is in effect"
    (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                 :attributes     {"impersonation_attr" "impersonation_role"}}
      (is (= "impersonation_role"
             (@#'impersonation/connection-impersonation-role (mt/db)))))))

(deftest connection-impersonation-role-test-3
  (testing "Throws exception if multiple conflicting impersonations are in effect"
    ;; Use nested `with-impersonations!` macros so that different groups are used
    (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr_1"}]
                                                 :attributes     {"impersonation_attr_1" "impersonation_role_1"}}
      (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr_2"}]
                                                   :attributes     {"impersonation_attr_2" "impersonation_role_2"}}
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Multiple conflicting connection impersonation policies found for current user"
             (@#'impersonation/connection-impersonation-role (mt/db))))))))

(deftest connection-impersonation-role-test-4
  (testing "Returns nil if the permissions in another group supercede the impersonation"
    (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                 :attributes     {"impersonation_attr" "impersonation_role"}}
      ;; `with-impersonations!` creates a new group and revokes data perms in `all users`, so if we re-grant data perms
      ;; for all users, it should supercede the impersonation policy in the new group
      (mt/with-full-data-perms-for-all-users!
        (is (nil? (@#'impersonation/connection-impersonation-role (mt/db))))))))

(deftest connection-impersonation-role-test-5
  (testing "Returns nil for superuser, even if they are in a group with an impersonation policy defined"
    (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                 :attributes     {"impersonation_attr" "impersonation_role"}}
      (request/as-admin
        (is (nil? (@#'impersonation/connection-impersonation-role (mt/db))))))))

(deftest connection-impersonation-role-test-6
  (testing "Does not throw an exception if passed a nil `database-or-id`"
    (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                 :attributes     {"impersonation_attr" "impersonation_role"}}
      (is (nil? (@#'impersonation/connection-impersonation-role nil))))))

(deftest connection-impersonation-role-test-7
  (testing "Throws an exception if impersonation should be enforced, but the user doesn't have the required attribute"
    (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                 :attributes     {}}
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"User does not have attribute required for connection impersonation."
           (@#'impersonation/connection-impersonation-role (mt/db)))))))

(deftest connection-impersonation-role-test-8
  (testing "Throws an exception if impersonation should be enforced, but the user's attribute is not a single string"
    (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                 :attributes     {"impersonation_attr" ["one" "two" "three"]}}
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Connection impersonation attribute is invalid: role must be a single non-empty string."
           (@#'impersonation/connection-impersonation-role (mt/db)))))))

(deftest connection-impersonation-role-test-9
  (testing "Throws an exception if sandboxing policies are also defined for the current user on the DB"
    (met/with-gtaps! {:gtaps {:venues {:query (mt/mbql-query venues)}}}
      (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                   :attributes     {"impersonation_attr" "impersonation_role"}}
        (mt/with-test-user :rasta
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Conflicting sandboxing and impersonation policies found."
               (@#'impersonation/connection-impersonation-role (mt/db)))))))))

(deftest conn-impersonation-test-postgres
  (mt/test-driver :postgres
    (mt/with-premium-features #{:advanced-permissions}
      (let [db-name "conn_impersonation_test"
            details (mt/dbdef->connection-details :postgres :db {:database-name db-name})
            spec    (sql-jdbc.conn/connection-details->spec :postgres details)]
        (postgres-test/drop-if-exists-and-create-db! db-name)
        (doseq [statement ["DROP TABLE IF EXISTS PUBLIC.table_with_access;"
                           "DROP TABLE IF EXISTS PUBLIC.table_without_access;"
                           "CREATE TABLE PUBLIC.table_with_access (x INTEGER NOT NULL);"
                           "CREATE TABLE PUBLIC.table_without_access (y INTEGER NOT NULL);"
                           "DROP ROLE IF EXISTS \"impersonation.role\";"
                           "CREATE ROLE \"impersonation.role\";"
                           "REVOKE ALL PRIVILEGES ON DATABASE \"conn_impersonation_test\" FROM \"impersonation.role\";"
                           "GRANT SELECT ON TABLE \"conn_impersonation_test\".PUBLIC.table_with_access TO \"impersonation.role\";"]]
          (jdbc/execute! spec [statement]))
        (mt/with-temp [:model/Database database {:engine :postgres, :details details}]
          (mt/with-db database (sync/sync-database! database)
            (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                         :attributes     {"impersonation_attr" "impersonation.role"}}
              (is (= []
                     (-> {:query "SELECT * FROM \"table_with_access\";"}
                         mt/native-query
                         mt/process-query
                         mt/rows)))
              (is (thrown-with-msg? clojure.lang.ExceptionInfo
                                    #"permission denied"
                                    (-> {:query "SELECT * FROM \"table_without_access\";"}
                                        mt/native-query
                                        mt/process-query
                                        mt/rows))))))))))

(deftest conn-impersonation-test-redshift
  (mt/test-driver :redshift
    (mt/with-premium-features #{:advanced-permissions}
      (let [details (mt/dbdef->connection-details :redshift nil nil)
            spec    (sql-jdbc.conn/connection-details->spec :redshift details)
            user    (u/lower-case-en (mt/random-name))
            schema  (sql.tx/session-schema :redshift)]
        (mt/with-temp [:model/Database database {:engine :redshift, :details details}]
          (try
            (doseq [statement [(format "DROP TABLE IF EXISTS \"%s\".table_with_access;" schema)
                               (format "DROP TABLE IF EXISTS \"%s\".table_without_access;" schema)
                               (format "CREATE TABLE \"%s\".table_with_access (x INTEGER NOT NULL);" schema)
                               (format "CREATE TABLE \"%s\".table_without_access (y INTEGER NOT NULL);" schema)
                               (format "CREATE USER %s WITH PASSWORD 'abcD1234';" user)
                               (format "GRANT ALL PRIVILEGES ON SCHEMA \"%s\" TO %s;" schema user)
                               (format "REVOKE ALL PRIVILEGES ON TABLE \"%s\".table_without_access FROM %s;" schema user)
                               (format "GRANT SELECT ON TABLE \"%s\".table_with_access TO %s;" schema user)]]
              (jdbc/execute! spec statement))
            (mt/with-db database
              (sync/sync-database! database {:scan :schema})
              (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                           :attributes     {"impersonation_attr" user}}
                (is (= []
                       (-> {:query (format "SELECT * FROM \"%s\".table_with_access;" schema)}
                           mt/native-query
                           mt/process-query
                           mt/rows)))
                (is (thrown-with-msg? clojure.lang.ExceptionInfo
                                      #"permission denied for relation table_without_access"
                                      (-> {:query (format "SELECT * FROM \"%s\".table_without_access;" schema)}
                                          mt/native-query
                                          mt/process-query
                                          mt/rows)))))
            (finally
              (doseq [statement [(format "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA \"%s\" FROM \"%s\"" schema user)
                                 (format "REVOKE ALL PRIVILEGES ON SCHEMA \"%s\" FROM \"%s\";" schema user)
                                 (format "DROP USER IF EXISTS %s;" user)]]
                (jdbc/execute! spec [statement])))))))))

(deftest conn-impersonation-test-snowflake
  (mt/test-driver :snowflake
    (mt/with-premium-features #{:advanced-permissions}
      (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                   :attributes     {"impersonation_attr" "LIMITED.ROLE"}}
        ;; Test database initially has no default role set. All queries should fail, even for non-impersonated users,
        ;; since there is no way to reset the connection after impersonation is applied.
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Connection impersonation is enabled for this database, but no default role is found"
             (mt/run-mbql-query venues
               {:aggregation [[:count]]})))
        (request/as-admin
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Connection impersonation is enabled for this database, but no default role is found"
               (mt/run-mbql-query venues
                 {:aggregation [[:count]]}))))

        ;; Update the test database with a default role that has full permissions
        (t2/update! :model/Database :id (mt/id) (assoc-in (mt/db) [:details :role] "ACCOUNTADMIN"))

        (try
          ;; User with connection impersonation should not be able to query a table they don't have access to
          ;; (`LIMITED.ROLE` in CI Snowflake has no data access)
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"SQL compilation error:\nDatabase.*does not exist or not authorized"
               (mt/run-mbql-query venues
                 {:aggregation [[:count]]})))

          ;; Non-impersonated user should stil be able to query the table
          (request/as-admin
            (is (= [100]
                   (mt/first-row
                    (mt/run-mbql-query venues
                      {:aggregation [[:count]]})))))
          (finally
            (t2/update! :model/Database :id (mt/id) (update (mt/db) :details dissoc :role))))))))

(deftest persistence-disabled-when-impersonated-test
  ;; Test explicitly with postgres since it supports persistence and impersonation
  (mt/test-driver :postgres
    (mt/with-premium-features #{:advanced-permissions}
      (mt/dataset test-data
        (mt/with-temp [:model/Card model {:type          :model
                                          :dataset_query (mt/mbql-query products)}]
          (mt/with-persistence-enabled! [persist-models!]
            (mt/as-admin (persist-models!))
            (advanced-perms.api.tu/with-impersonations! {:impersonations [{:db-id (mt/id) :attribute "impersonation_attr"}]
                                                         :attributes     {"impersonation_attr" "impersonation_role"}}
              (let [details (t2/select-one-fn :details :model/Database (mt/id))
                    spec    (sql-jdbc.conn/connection-details->spec :postgres details)]
                ;; Create impersonation_role on test DB so that the non-admin can execute queries
                (doseq [statement ["DROP ROLE IF EXISTS \"impersonation_role\";"
                                   "CREATE ROLE \"impersonation_role\";"
                                   "GRANT ALL PRIVILEGES ON TABLE \"products\" to \"impersonation_role\";"]]
                  (jdbc/execute! spec [statement]))
                (try
                  (let [persisted-info (t2/select-one :model/PersistedInfo
                                                      :database_id (mt/id)
                                                      :card_id (:id model))
                        query          (mt/mbql-query nil
                                         {:aggregation  [:count]
                                          :source-table (str "card__" (:id model))})
                        impersonated-result (mt/with-test-user :rasta (qp/process-query query))
                        ;; Make sure we run admin query second to reset the DB role on the connection for other tests!
                        admin-result        (mt/as-admin (qp/process-query query))]
                    (testing "Impersonated user (rasta) does not hit the model cache"
                      (is (not (str/includes? (-> impersonated-result :data :native_form :query)
                                              (:table_name persisted-info)))
                          "Erroneously used the persisted model cache"))

                    (testing "Query from admin hits the model cache"
                      (is (str/includes? (-> admin-result :data :native_form :query)
                                         (:table_name persisted-info))
                          "Did not use the persisted model cache")))
                  (finally
                    (doseq [statement ["REVOKE ALL PRIVILEGES ON TABLE \"products\" FROM \"impersonation_role\";"
                                       "DROP ROLE IF EXISTS \"impersonation_role\";"]]
                      (jdbc/execute! spec [statement]))))))))))))
