/*
Copyright 2023 Google LLC
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package functions;

import static functions.ScanProjectQuotas.THRESHOLD;

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.monitoring.v3.QueryServiceClient.QueryTimeSeriesPagedResponse;
import com.google.cloud.monitoring.v3.QueryServiceClient;
import com.google.monitoring.v3.QueryTimeSeriesRequest;
import com.google.monitoring.v3.TimeSeriesData;
import com.google.monitoring.v3.TimeSeriesDescriptor;
import com.google.monitoring.v3.TimeSeriesData.PointData;

import functions.eventpojos.GCPProject;
import functions.eventpojos.GCPResourceClient;
import functions.eventpojos.ProjectQuota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanProjectQuotasHelper {
  private static final Logger logger = LoggerFactory.getLogger(ScanProjectQuotas.class);

  public static final String MQL_ALLOCATION_ALL = "fetch consumer_quota" +
  "| { current: metric serviceruntime.googleapis.com/quota/allocation/usage" +  
  "    | filter resource.project_id = '%1$s'" +
  "    | align next_older(1w)" +
  "    | every 1w" +
  "  ; maximum: metric serviceruntime.googleapis.com/quota/allocation/usage" +
  "    | filter resource.project_id = '%1$s'" +
  "    | group_by 1w, [value_usage_max: max(value.usage)]" +
  "    | every 1w" +
  "  ; limit: metric 'serviceruntime.googleapis.com/quota/limit'" +
  "    | filter resource.project_id = '%1$s'" +
  "    | align next_older(1w)" +
  "    | every 1w" +
  "    }" +
  "| join" +
  "| value [current: val(0), maximum: val(1), limit: val(2)]";

  // MQL to fetch rate quotas on a per minute basis
  public static final String MQL_RATE_QPM = "fetch consumer_quota" +
  "| { current: metric serviceruntime.googleapis.com/quota/rate/net_usage" +
  "    | filter resource.project_id = '%1$s'" +
  "    | every 1m" +
  "    | within 1w" +
  "  ; maximum: metric serviceruntime.googleapis.com/quota/rate/net_usage" +
  "    | filter resource.project_id = '%1$s'" +
  "    | group_by 1w, [value_usage_max: max(value.net_usage)]" +
  "    | every 1m" +
  "    | within 1w" +
  "  ; limit: metric 'serviceruntime.googleapis.com/quota/limit'" +
  "    | filter resource.project_id = '%1$s'" +
  "      && !(metric.limit_name =~ '.*GoogleEgressBandwidth.*'" +
  "        || metric.limit_name =~ '.*EGRESS-BANDWIDTH.*'" +
  "        || metric.limit_name =~ '.*PerDay.*'" +
  "        || metric.limit_name =~ '.*Qpd.*')" +
  "    | align next_older(1m)" +
  "    | every 1m" +
  "    | within 1w" +
  "    }" +
  "| join" +
  "| value [current: val(0), maximum: val(1), limit: val(2)]";

  // MQL to fetch rate quotas on a per second basis
  public static final String MQL_RATE_QPS = "fetch consumer_quota" +
  "| { current:" +
  "      metric serviceruntime.googleapis.com/quota/rate/net_usage" +
  "      | filter" +
  "          resource.project_id = '%1$s'" +
  "      | every 1s" +
  "      | within 1d" +
  "  ; maximum:" +
  "      metric serviceruntime.googleapis.com/quota/rate/net_usage" +
  "      | filter" +
  "          resource.project_id = '%1$s'" +
  "      | group_by 1d, [value_usage_max: max(value.net_usage)]" +
  "      | every 1s" +
  "      | within 1d" +
  "  ; limit:" +
  "      metric serviceruntime.googleapis.com/quota/limit" +
  "      | filter" +
  "          resource.project_id = '%1$s'" +
  "          && (metric.limit_name =~ '.*GoogleEgressBandwidth.*'" +
  "              || metric.limit_name =~ '.*EGRESS-BANDWIDTH.*')" +
  "      | align next_older(1m)" +
  "      | every 1s" +
  "      | within 1d" +
  " }" +
  "| join" + 
  "| value [current: val(0), maximum: val(1), limit: val(2)]";

  // MQL to get the usage aggregated on a daily basis
  // Based on filter provided at https://cloud.google.com/monitoring/alerts/using-quota-metrics#mql-rate-multiple-limits
  public static final String MQL_RATE_QPD = "fetch consumer_quota" +
  "| { daily:" +
  "      metric serviceruntime.googleapis.com/quota/rate/net_usage" +
  "      | filter" +
  "          resource.project_id = '%1$s'" +
  "      | group_by 1d, [value_usage_sum: sum(value.net_usage)]" +
  "      | every 1d" +
  "      | within 1w, d'%2$s'" +
  "  ; limit:" +
  "      metric serviceruntime.googleapis.com/quota/limit" +
  "      | filter" +
  "          resource.project_id = '%1$s'" +
  "          && (metric.limit_name =~ '.*PerDay.*'" +
  "              || metric.limit_name =~ '.*Qpd.*')" +
  "      | align next_older(1d)" +
  "      | every 1d" +
  "      | within 1w, d'%2$s'" +
  " }" +
  "| join" +
  "| value [daily: val(0), limit: val(1)]";

  enum Quotas {
    ALLOCATION,
    RATE
  }

  /*
   * API to create GCP Resource Client for BigQuery Tables
   * */
  static GCPResourceClient createGCPResourceClient() {
    String datasetName = ScanProjectQuotas.BIG_QUERY_DATASET;
    String tableName = ScanProjectQuotas.BIG_QUERY_TABLE;
    // Initialize client that will be used to send requests.
    BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
    // Get table
    TableId tableId = TableId.of(datasetName, tableName);
    GCPResourceClient gcpResourceClient = new GCPResourceClient();
    gcpResourceClient.setBigQuery(bigquery);
    gcpResourceClient.setTableId(tableId);
    return gcpResourceClient;
  }

  public static List<ProjectQuota> getQuota(GCPProject gcpProject, Quotas quota) {
    List<ProjectQuota> projectQuotas = new ArrayList<>();

    try (QueryServiceClient queryServiceClient = QueryServiceClient.create()) {
      QueryTimeSeriesRequest request =
          QueryTimeSeriesRequest.newBuilder()
              .setName(gcpProject.getProjectName())
              .setQuery(String.format(getMql(quota), gcpProject.getProjectId()))
              .build();

      Timestamp ts = Timestamp.now();
      QueryTimeSeriesPagedResponse response = queryServiceClient.queryTimeSeries(request);
      HashMap<String, Integer> indexMap = buildIndexMap(response.getPage().getResponse().getTimeSeriesDescriptor());
      for (TimeSeriesData data : response.iterateAll()) {
          projectQuotas.add(populateProjectQuota(data, null, ts, indexMap, quota));
      }

      // Get the QPD and QPS quotas
      if(quota == Quotas.RATE) {
        projectQuotas.addAll(getPerDayQuota(gcpProject, ts));
        
        projectQuotas.addAll(getPerSecondQuota(gcpProject, ts));
      }

    } catch (IOException e) {
      MDC.put("severity", "ERROR");
      logger.error(
          "Error fetching timeseries data for project: "
              + gcpProject.getProjectName()
              + e.getMessage(),
          e);
    }

    return projectQuotas;
  }

  private static List<ProjectQuota> getPerSecondQuota(GCPProject gcpProject, Timestamp ts) {
    HashMap<String, ProjectQuota> projectQuotas = new HashMap<>();

    try (QueryServiceClient queryServiceClient = QueryServiceClient.create()) {
      String mql =
          String.format(MQL_RATE_QPS,
              gcpProject.getProjectId()
          );

      QueryTimeSeriesRequest request =
          QueryTimeSeriesRequest.newBuilder()
              .setName(gcpProject.getProjectName())
              .setQuery(mql)
              .build();

      QueryTimeSeriesPagedResponse response = queryServiceClient.queryTimeSeries(request);
      HashMap<String, Integer> indexMap = buildIndexMap(response.getPage().getResponse().getTimeSeriesDescriptor());
      for (TimeSeriesData data : response.iterateAll()) {
        String key = data.getLabelValues(indexMap.get("metric.limit_name")).getStringValue() + data.getLabelValues(indexMap.get("resource.location")).getStringValue();
        projectQuotas.put(key, populateProjectQuota(data, null, ts, indexMap, Quotas.RATE));   
      }
    } catch (IOException e) {
      MDC.put("severity", "ERROR");
      logger.error(
          "Error fetching timeseries data for project: "
              + gcpProject.getProjectName()
              + e.getMessage(),
          e);
    }

    return new ArrayList<ProjectQuota>(projectQuotas.values());
  }

  private static List<ProjectQuota> getPerDayQuota(GCPProject gcpProject, Timestamp ts) {
    List<ProjectQuota> projectQuotas = new ArrayList<>();
    HashMap<String, Long> values = new HashMap<>() {{
      put("current", (long) 0);
      put("max", (long) 0);
    }};

    try (QueryServiceClient queryServiceClient = QueryServiceClient.create()) {
      // This needs to align to the day boundaries as closely as possible to that we get an
      // accurate view into same window as the quota system.
      LocalDate today = LocalDate.now();
      ZonedDateTime endOfDay = ZonedDateTime.of(today, LocalTime.MAX, ZoneId.of("America/Los_Angeles"));
      
      String mql =
          String.format(MQL_RATE_QPD,
              gcpProject.getProjectId(),
              endOfDay.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
          );

      QueryTimeSeriesRequest request =
          QueryTimeSeriesRequest.newBuilder()
              .setName(gcpProject.getProjectName())
              .setQuery(mql)
              .build();

      QueryTimeSeriesPagedResponse response = queryServiceClient.queryTimeSeries(request);
      HashMap<String, Integer> indexMap = buildIndexMap(response.getPage().getResponse().getTimeSeriesDescriptor());
      for (TimeSeriesData data : response.iterateAll()) {
        List<PointData> stuffs = data.getPointDataList();
        MDC.put("severity", "INFO");
        for (PointData pointData : stuffs) {
         logger.info(String.format("Metric: %s, Current: %d, Max %d, Value %d%n", 
            data.getLabelValues(indexMap.get("metric.quota_metric")).getStringValue(),
            values.get("current"), values.get("max"),
            pointData.getValues(0).getInt64Value()));

          // Cloud Monitoring returns UTC timestamps so we need to use end of day UTC to match correctly.
          if (pointData.getTimeInterval().getEndTime().getSeconds() == ZonedDateTime.of(today, LocalTime.MAX, ZoneId.of("UTC")).toEpochSecond()) {
            values.replace("current", pointData.getValues(0).getInt64Value());
          }

          if (pointData.getValues(0).getInt64Value() > values.get("max")) {
            values.replace("max", pointData.getValues(0).getInt64Value());
          }
        }

        projectQuotas.add(populateProjectQuota(data, values, ts, indexMap, Quotas.RATE));
      }
    } catch (IOException e) {
      MDC.put("severity", "ERROR");
      logger.error(
          "Error fetching timeseries data for project: "
              + gcpProject.getProjectName()
              + e.getMessage(),
          e);
    }

    return projectQuotas;
  }

  private static HashMap<String, Integer> buildIndexMap(TimeSeriesDescriptor labels) {
    HashMap<String, Integer> indexMap = new HashMap<>();
    
    for(int i=0; i<labels.getLabelDescriptorsCount(); i++) {
      indexMap.put(labels.getLabelDescriptors(i).getKey(), i);
    }

    for(int i=0; i<labels.getPointDescriptorsCount(); i++) {
      indexMap.put(labels.getPointDescriptors(i).getKey(), i);
    }

    return indexMap;
  }

  private static ProjectQuota populateProjectQuota(
      TimeSeriesData data, HashMap<String, Long> aggregatedData, Timestamp ts, HashMap<String, Integer> indexMap, Quotas q) {
    ProjectQuota projectQuota = new ProjectQuota();

    projectQuota.setProjectId(data.getLabelValues(indexMap.get("resource.project_id")).getStringValue());
    projectQuota.setTimestamp(ts.toString());
    projectQuota.setRegion(data.getLabelValues(indexMap.get("resource.location")).getStringValue());
    projectQuota.setMetric(data.getLabelValues(indexMap.get("metric.quota_metric")).getStringValue());

    if(q == Quotas.RATE) {
      projectQuota.setApiMethod(data.getLabelValues(indexMap.get("metric.method")).getStringValue());
    }
    projectQuota.setLimitName(data.getLabelValues(indexMap.get("metric.limit_name")).getStringValue());
    projectQuota.setQuotaType(q.toString());

    if(aggregatedData == null) {
      projectQuota.setCurrentUsage(data.getPointData(0).getValues(indexMap.get("current")).getInt64Value());
      projectQuota.setMaxUsage(data.getPointData(0).getValues(indexMap.get("maximum")).getInt64Value());
    } else {
      projectQuota.setCurrentUsage(aggregatedData.get("current"));
      projectQuota.setMaxUsage(aggregatedData.get("max"));
    }

    projectQuota.setQuotaLimit(data.getPointData(0).getValues(indexMap.get("limit")).getInt64Value());
    projectQuota.setThreshold(Integer.valueOf(THRESHOLD));

    return projectQuota;
  }

  private static String getMql(Quotas q) {
    String mql;

    switch (q) {
      case ALLOCATION:
        mql = MQL_ALLOCATION_ALL;
        break;
      case RATE:
        mql = MQL_RATE_QPM;
        break;
      default:
        mql = "";
    }

    return mql;
  }

  /*
   * API to load data into BigQuery
   * */
  static void loadBigQueryTable(
      GCPResourceClient gcpResourceClient,
      List<ProjectQuota> projectQuotas) {
    for (ProjectQuota pq : projectQuotas) {
      Map<String, Object> row = createBQRow(pq);
      tableInsertRows(gcpResourceClient, row);
    }
  }

  /*
   * API to build BigQuery row content from ProjectQuota object
   * */
  public static Map<String, Object> createBQRow(ProjectQuota projectQuota) {
    Map<String, Object> rowContent = new HashMap<>();

    rowContent.put("project_id", projectQuota.getProjectId());
    rowContent.put("added_at", projectQuota.getTimestamp());
    rowContent.put("region", projectQuota.getRegion());
    rowContent.put("quota_metric", projectQuota.getMetric());
    if(projectQuota.getQuotaType() == Quotas.RATE.toString()) {
      rowContent.put("api_method", projectQuota.getApiMethod());
    }
    rowContent.put("limit_name", projectQuota.getLimitName());
    rowContent.put("quota_type", projectQuota.getQuotaType());
    rowContent.put("current_usage", projectQuota.getCurrentUsage());
    rowContent.put("max_usage", projectQuota.getMaxUsage());
    rowContent.put("quota_limit", projectQuota.getQuotaLimit());
    rowContent.put("threshold", projectQuota.getThreshold());
    
    return rowContent;
  }

  /*
   * API to insert row in table
   * */
  public static void tableInsertRows(
      GCPResourceClient gcpResourceClient, Map<String, Object> rowContent) {

    try {
      // Initialize client that will be used to send requests. This client only needs to be created
      // once, and can be reused for multiple requests.
      BigQuery bigquery = gcpResourceClient.getBigQuery();
      // Get table
      TableId tableId = gcpResourceClient.getTableId();
      // Inserts rowContent into datasetName:tableId.
      InsertAllResponse response =
          bigquery.insertAll(InsertAllRequest.newBuilder(tableId).addRow(rowContent).build());

      if (response.hasErrors()) {
        // If any of the insertions failed, this lets you inspect the errors
        MDC.put("severity", "ERROR");
        for (Map.Entry<Long, List<BigQueryError>> entry : response.getInsertErrors().entrySet()) {
          logger.error( "Bigquery row insert response error: " + entry.getValue());
        }
      }
    } catch (BigQueryException e) {
      MDC.put("severity", "ERROR");
      logger.error("Insert operation not performed: " + e.toString());
    }
  }
}
