import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.FileAppender

import static ch.qos.logback.classic.Level.DEBUG

def bySecond = timestamp("yyyyMMdd'T'HHmmss")

appender("FILE", FileAppender) {
  file = "vision-client-2017-${bySecond}.log"
  append = true
  immediateFlush = true
  encoder(PatternLayoutEncoder) {
    pattern = "[%thread/%level][%logger{35}]: %msg%n"
  }
}
root(DEBUG, ["FILE"])