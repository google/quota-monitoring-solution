/*
Copyright 2022 Google LLC

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
  private String metric;
  private String usage;
  private String limit;
  private Float consumption;

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

  public String getMetric() {
    return metric;
  }

  public void setMetric(String metric) {
    this.metric = metric;
  }

  public String getUsage() {
    return usage;
  }

  public void setUsage(String usage) {
    this.usage = usage;
  }

  public String getLimit() {
    return limit;
  }

  public void setLimit(String limit) {
    this.limit = limit;
  }

  public Float getConsumption() {
    return consumption;
  }

  public void setConsumption(Float consumption) {
    this.consumption = consumption;
  }

  public String toString(){
    StringBuilder alertBuilder = new StringBuilder();
    alertBuilder.append("|" + projectId);
    alertBuilder.append("|"+region);
    alertBuilder.append("|`"+metric.replace(".com"," .com")+"`");
    //alertBuilder.append("|"+usage);
    //alertBuilder.append("|"+limit);
    alertBuilder.append("|"+consumption);
    return alertBuilder.toString();
  }
}


