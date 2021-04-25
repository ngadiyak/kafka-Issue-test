### CASE:

If Kafka has been disconnected from the network when producer is sending messages to the topic (with small delay between messages and without retries) 
there is a situation we reproduce in the test. Producer gets an exception and Kafka saves expired message.

**Producer's config:**
```
retries = 0
request.timeout.ms = 100
delivery.timeout.ms = 100
max.in.flight.requests.per.connection = 1
```

### HOW TO RUN:

Run command `sbt "compile;testOnly"` from th project root.

### LOG:
```
[info] Test:
[info] Kafka
--- Start sending messages to kafka ---
--- Sleep 1 second ---
Message [1]: Success
Message [2]: Success
Message [3]: Success
Message [4]: Success
Message [5]: Success
--- Disconnecting Kafka from the network ---
Message [6]: Success
Message [7]: Success
Message [8]: Success
Message [9]: Callback Exception: org.apache.kafka.common.errors.NetworkException: The server disconnected before a response was received.
Message [10]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:100 ms has passed since batch creation
Message [11]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:101 ms has passed since batch creation
Message [12]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:100 ms has passed since batch creation
Message [13]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:100 ms has passed since batch creation
Message [14]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:101 ms has passed since batch creation
--- Kafka is disconnected from the network ---
Message [15]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:100 ms has passed since batch creation
Message [16]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:100 ms has passed since batch creation
Message [17]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:100 ms has passed since batch creation
Message [18]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:101 ms has passed since batch creation
Message [19]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:100 ms has passed since batch creation
Message [20]: Callback Exception: org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:100 ms has passed since batch creation
Last message sent without error: [8]
--- Connecting Kafka to the network ---
--- Kafka is connected to the network ---
--- Start consuming ---
Consumed message: [1]
Consumed message: [2]
Consumed message: [3]
Consumed message: [4]
Consumed message: [5]
Consumed message: [6]
Consumed message: [7]
Consumed message: [8]
Consumed message: [9]
[info] - should not put failed message into the topic *** FAILED ***
[info]   9 was not equal to 8 (Test.scala:216)
[info] Run completed in 24 seconds, 213 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 0, failed 1, canceled 0, ignored 0, pending 0
[info] *** 1 TEST FAILED ***
[error] Failed tests:
[error]         kafka.issue.Test
[error] (Test / testOnly) sbt.TestsFailedException: Tests unsuccessful
[error] Total time: 28 s, completed Apr 25, 2021 12:45:41 PM
```
