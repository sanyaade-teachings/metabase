import Settings from "metabase/lib/settings";
import { formatSQL } from "metabase/lib/formatting";

export function getDefaultEngine() {
  const engines = Object.keys(Settings.get("engines"));
  return engines.includes("postgres") ? "postgres" : engines[0];
}

export function getEngineNativeType(engine) {
  switch (engine) {
    case "mongo":
      return "json";
    case "druid":
      return "json";
    // TODO: Remove this code related to Google Analytics?
    case "googleanalytics":
      return "json";
    default:
      return "sql";
  }
}

export function getNativeQueryLanguage(engine) {
  return getEngineNativeType(engine).toUpperCase();
}

export function getEngineNativeAceMode(engine) {
  switch (engine) {
    case "mongo":
      return "ace/mode/json";
    case "druid":
      return "ace/mode/json";
    // TODO: Remove this code related to Google Analytics?
    case "googleanalytics":
      return "ace/mode/json";
    default:
      return "ace/mode/sql";
  }
}

export function getEngineLogo(engine) {
  const path = `app/assets/img/drivers`;

  switch (engine) {
    case "bigquery":
      return `${path}/${engine}.svg`;
    case "druid":
      return `${path}/${engine}.svg`;
    // TODO: Remove this code related to Google Analytics?
    case "googleanalytics":
      return `${path}/${engine}.svg`;
    case "h2":
      return `${path}/${engine}.svg`;
    case "mongo":
      return `${path}/${engine}.svg`;
    case "mysql":
      return `${path}/${engine}.svg`;
    case "oracle":
      return `${path}/${engine}.svg`;
    case "postgres":
      return `${path}/${engine}.svg`;
    case "redshift":
      return `${path}/${engine}.svg`;
    case "snowflake":
      return `${path}/${engine}.svg`;
    case "sparksql":
      return `${path}/${engine}.svg`;
    case "sqlite":
      return `${path}/${engine}.svg`;
    case "sqlserver":
      return `${path}/${engine}.svg`;
    case "vertica":
      return `${path}/${engine}.svg`;
    case "bigquery-cloud-sdk":
      return `${path}/bigquery.svg`;
    case "presto-jdbc":
      return `${path}/presto.svg`;
    case "starburst":
      return `${path}/starburst.svg`;
    case "materialize":
      return `${path}/materialize.svg`;
  }
}

export function getElevatedEngines() {
  return [
    "mysql",
    "postgres",
    "sqlserver",
    "redshift",
    "bigquery-cloud-sdk",
    "snowflake",
  ];
}

export function getEngineSupportsFirewall(engine) {
  // TODO: Amend this code related to Google Analytics?
  return engine !== "googleanalytics";
}

export function formatJsonQuery(query, engine) {
  // TODO: Amend this code related to Google Analytics?
  if (engine === "googleanalytics") {
    return formatGAQuery(query);
  }

  return JSON.stringify(query, null, 2);
}

export function formatNativeQuery(query, engine) {
  return getEngineNativeType(engine) === "json"
    ? formatJsonQuery(query, engine)
    : formatSQL(query);
}

export function isDeprecatedEngine(engine) {
  const engines = Settings.get("engines", {});
  return engines[engine] != null && engines[engine]["superseded-by"] != null;
}

// TODO: Remove this code related to Google Analytics?
const GA_ORDERED_PARAMS = [
  "ids",
  "start-date",
  "end-date",
  "metrics",
  "dimensions",
  "sort",
  "filters",
  "segment",
  "samplingLevel",
  "include-empty-rows",
  "start-index",
  "max-results",
];

// TODO: Remove this code related to Google Analytics?
// does 3 things: removes null values, sorts the keys by the order in the documentation, and formats with 2 space indents
function formatGAQuery(query) {
  if (!query) {
    return "";
  }
  const object = {};
  for (const param of GA_ORDERED_PARAMS) {
    if (query[param] != null) {
      object[param] = query[param];
    }
  }
  return JSON.stringify(object, null, 2);
}
