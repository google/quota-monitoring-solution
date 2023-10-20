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

import static functions.ScanProjectQuotasHelper.createGCPResourceClient;
import static functions.ScanProjectQuotasHelper.getQuota;
import static functions.ScanProjectQuotasHelper.loadBigQueryTable;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.monitoring.v3.ProjectName;
import functions.eventpojos.GCPProject;
import functions.eventpojos.GCPResourceClient;
import functions.eventpojos.PubSubMessage;
import functions.eventpojos.ProjectQuota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class ScanProjectQuotas implements BackgroundFunction<PubSubMessage> {
  private static final Logger logger = LoggerFactory.getLogger(ScanProjectQuotas.class);

  // Cloud Function Environment variable for Threshold
  public static final String THRESHOLD = System.getenv("THRESHOLD");
  // BigQuery Dataset name
  public static final String BIG_QUERY_DATASET = System.getenv("BIG_QUERY_DATASET");
  // BigQuery Table name
  public static final String BIG_QUERY_TABLE = System.getenv("BIG_QUERY_TABLE");

  /*
   * API to accept request to Cloud Function
   * */
  @Override
  public void accept(PubSubMessage message, Context context) {
    if (message.getData() == null) {
      MDC.put("severity", "WARN");
      logger.warn( "No Project Id provided");
      return;
    }
    // project Id received from Pub/Sub topic
    String projectId =
        new String(
            Base64.getDecoder().decode(message.getData().getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8);
    try {
      GCPProject gcpProject = new GCPProject();
      gcpProject.setProjectId(projectId);
      gcpProject.setProjectName(ProjectName.of(projectId).toString());
      GCPResourceClient gcpResourceClient = createGCPResourceClient();

      // 1. Scan Allocation quota and load in main table in BigQuery
      getAllocationUsageQuotas(gcpResourceClient, gcpProject);
      // 2. Scan Rate quotas and load in main table
      getRateUsageQuotas(gcpResourceClient, gcpProject);
    } catch (Exception e) {
      MDC.put("severity", "ERROR");
      logger.error( " " + e.getMessage(), e);
    }
  }

  /*
   * API to get all Allocation quotas usage for this project
   * */
  private static void getAllocationUsageQuotas(
      GCPResourceClient gcpResourceClient, GCPProject gcpProject) {
    try {
      scanQuota(
        gcpResourceClient,
        gcpProject,
        ScanProjectQuotasHelper.Quotas.ALLOCATION
      );
    } catch (IOException e) {
      MDC.put("severity", "ERROR");
      logger.error("Error fetching Allocation usage quotas " + e.getMessage(), e);
    }
  }

  /*
   * API to get all Rate quotas usage for this project
   * */
  private static void getRateUsageQuotas(
      GCPResourceClient gcpResourceClient, GCPProject gcpProject) {
    try {
      scanQuota(
        gcpResourceClient,
        gcpProject,
        ScanProjectQuotasHelper.Quotas.RATE
      );
    } catch (IOException e) {
      MDC.put("severity", "ERROR");
      logger.error("Error fetching Rate usage quotas  " + e.getMessage(), e);
    }
  }

  /*
   * API to get quotas from APIs and load in BigQuery
   * */
  private static void scanQuota(
      GCPResourceClient gcpResourceClient,
      GCPProject gcpProject,
      ScanProjectQuotasHelper.Quotas q)
      throws IOException {
    List<ProjectQuota> projectQuotas = getQuota(gcpProject, q);
    loadBigQueryTable(gcpResourceClient, projectQuotas);
    MDC.put("severity", "INFO");
    logger.info("Quotas loaded successfully for project Id:" + gcpProject.getProjectId());
  }
}
