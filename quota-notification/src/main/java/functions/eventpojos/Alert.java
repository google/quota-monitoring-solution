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
package functions.eventpojos;

public class Alert {
  private String projectId;
  private String region;
  private String quotaMetric;
  private String currentUsage;
  private String quotaLimit;
  private Float currentConsumption;

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getQuotaMetric() {
    return quotaMetric;
  }

  public void setQuotaMetric(String quotaMetric) {
    this.quotaMetric = quotaMetric;
  }

  public String getCurrentUsage() {
    return currentUsage;
  }

  public void setCurrentUsage(String currentUsage) {
    this.currentUsage = currentUsage;
  }

  public String getQuotaLimit() {
    return quotaLimit;
  }

  public void setQuotaLimit(String quotaLimit) {
    this.quotaLimit = quotaLimit;
  }

  public Float getCurrentConsumption() {
    return currentConsumption;
  }

  public void setCurrentConsumption(Float currentConsumption) {
    this.currentConsumption = currentConsumption;
  }

  public String toString(){
    StringBuilder alertBuilder = new StringBuilder();
    alertBuilder.append("|" + projectId);
    alertBuilder.append("|"+region);
    alertBuilder.append("|`"+ quotaMetric.replace(".com"," .com")+"`");
    //alertBuilder.append("|"+usage);
    //alertBuilder.append("|"+limit);
    alertBuilder.append("|"+ currentConsumption);
    return alertBuilder.toString();
  }
}


