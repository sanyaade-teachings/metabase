(ns metabase-enterprise.metabot-v3.tools.util
  (:require
   [medley.core :as m]
   [metabase.lib.core :as lib]
   [metabase.lib.options :as lib.options]
   [metabase.lib.types.isa :as lib.types.isa]
   [metabase.models.interface :as mi]
   [metabase.util :as u]
   [toucan2.core :as t2]))

(defn convert-field-type
  "Return tool type for `column`."
  [column]
  (let [column (u/normalize-map column)]
    (cond
      (lib.types.isa/boolean? column)               "boolean"
      (lib.types.isa/string-or-string-like? column) "string"
      (lib.types.isa/numeric? column)               "number"
      (lib.types.isa/temporal? column)              "date")))

(defn ->result-column
  "Return an tool result columns for `column` prefixing :id with `field-id-prefix`."
  [column field-id-prefix]
  (-> {:id (str field-id-prefix (-> column
                                    lib/ref
                                    (lib.options/update-options dissoc :lib/uuid)
                                    pr-str))
       :name (get column :display-name)
       :type (convert-field-type column)}
      (m/assoc-some :description (get column :description))))

(defn get-table
  "Get the `fields` of the table with ID `id`."
  [id & fields]
  (when-let [table (t2/select-one (into [:model/Table :id] fields) id)]
    (when (mi/can-read? table)
      table)))
