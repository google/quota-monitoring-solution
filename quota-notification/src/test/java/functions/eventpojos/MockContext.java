package functions.eventpojos;

import com.google.cloud.functions.Context;

public class MockContext implements Context {
  public String eventId;
  public String eventType;
  public String timestamp;
  public String resource;

  @Override
  public String eventId() {
    return this.eventId;
  }

  @Override
  public String timestamp() {
    return this.timestamp;
  }

  @Override
  public String eventType() {
    return this.eventType;
  }

  @Override
  public String resource() {
    return this.resource;
  }

}
