package kafka.issue

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.util.Collections
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{blocking, Await, Future, Promise}
import scala.jdk.DurationConverters._
import scala.util.Using

class Test extends AnyFlatSpec with HasKafka with AsyncUtils with TimerUtils {

  private val topicName = "test_topic"

  "Kafka" should "not put failed message into the topic" in {
    val topicPartitions = Collections.singletonList(new TopicPartition(topicName, 0))
    Using.resource(mkProducer) { producer =>
      val number = 50
      val delay = 25.millis

      val sendFuture = sendMessagesAsync(producer, number, delay)

      blocking(Thread.sleep((number * delay).toMillis)) // sendFuture works here in other thread
      disconnectKafkaFromNetwork()

      // Here we expect 0 messages will be sent to Kafka

      val sentSuccessfully = blocking(Await.result(sendFuture, (number * delay) * 121)) // 120 ms delivery
      log.info(s"--- Sent successfully: $sentSuccessfully ---")
      connectKafkaToNetwork()

      // Here we expect 0 messages will be sent to Kafka too because of a timeout
      // But some "stale" messages are written!
      Using.resource(mkConsumer) { consumer =>
        log.info(s"--- Start consuming, expecting $sentSuccessfully messages ---")
        consumer.assign(topicPartitions)
        consumer.seekToBeginning(topicPartitions)

        var numberOfRead = 0
        while (numberOfRead < sentSuccessfully) {
          val xs = consumer.poll(2.seconds.toJava)
          xs.forEach { r =>
            log.info(s"Consumed message: [${r.value()}]")
          }
          numberOfRead += xs.count()
        }

        numberOfRead shouldBe sentSuccessfully
      }
    }
  }

  /**
   * @return Number of messages those are sent successfully
   */
  private def sendMessagesAsync(producer: KafkaProducer[Null, Int], number: Int, delay: FiniteDuration): Future[Int] = {
    log.info("--- Start sending messages to kafka ---")

    val messages = (1 to number).map(new ProducerRecord(topicName, null, _))
    sequence(messages) { m =>
      val promise = Promise[Boolean]()
      producer.send(
        m,
        { (_: RecordMetadata, exception: Exception) =>
          val failed = Option(exception)
          log.info(failed match {
            case Some(e) => s"Message [${m.value()}]: Callback Exception: $e"
            case None => s"Message [${m.value()}]: Success"
          })
          promise.success(failed.isEmpty)
        }
      )

      for {
        r <- promise.future
        _ <- sleep(delay)
      } yield r
    }.map(_.count(_ == true))
  }

}
