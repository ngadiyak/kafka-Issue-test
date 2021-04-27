package kafka.issue

import org.slf4j.LoggerFactory

trait Logging {
  protected val log = LoggerFactory.getLogger("Test")
}
