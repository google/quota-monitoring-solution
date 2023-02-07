package functions;

import static com.google.common.truth.Truth.assertThat;
import com.google.common.testing.TestLogHandler;
import functions.eventpojos.GcsEvent;
import functions.eventpojos.MockContext;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConfigureAppAlertTest {
  private static final TestLogHandler LOG_HANDLER = new TestLogHandler();
  private static final Logger logger = Logger.getLogger(ConfigureAppAlert.class.getName());

  @Before
  public void beforeTest() throws Exception {
    logger.addHandler(LOG_HANDLER);
  }

  @After
  public void afterTest() {
    LOG_HANDLER.clear();
  }

  @Test
  public void helloGcs_shouldPrintFileName() throws Exception {
    GcsEvent event = new GcsEvent();
    event.setName("foo.txt");

    MockContext context = new MockContext();
    context.eventType = "google.storage.object.finalize";

    new ConfigureAppAlert().accept(event, context);

    String message = LOG_HANDLER.getStoredLogRecords().get(3).getMessage();
    assertThat(message).contains("File: foo.txt");
  }

}
