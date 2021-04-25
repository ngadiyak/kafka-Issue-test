package kafka.issue

import com.dimafeng.testcontainers.KafkaContainer
import com.github.dockerjava.api.command.CreateNetworkCmd
import com.github.dockerjava.api.model.{NetworkSettings, PortBinding}
import io.netty.util.{HashedWheelTimer, Timer}
import GlobalTimer.TimerOpsImplicits
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.testcontainers.containers.Network
import org.testcontainers.containers.Network.NetworkImpl

import java.util
import java.util.Properties
import scala.collection.JavaConverters._
import scala.collection.immutable.Queue
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Random, Success, Try}

object GlobalTimer {

  val instance: Timer = new HashedWheelTimer()

  sys.addShutdownHook {
    instance.stop()
  }

  implicit class TimerOpsImplicits(val timer: Timer) extends AnyVal {

    def schedule[A](f: => Future[A], delay: FiniteDuration): Future[A] = {
      val p = Promise[A]()
      try timer.newTimeout(_ => p.completeWith(f), delay.length, delay.unit)
      catch {
        case NonFatal(e) => p.failure(e)
      }
      p.future
    }

    def sleep(term: FiniteDuration): Future[Unit] = schedule(Future.successful(()), term)
  }

}

object Implicits {

  implicit final class FutureOps[T](val self: Future[T]) extends AnyVal {
    def safe(implicit ec: ExecutionContext): Future[Try[T]] = self.transform(x => Success(x))
  }

  implicit final class FutureCompanionOps(val self: Future.type) extends AnyVal {

    def inSeries[A, B](xs: Iterable[A])(f: A => Future[B])(implicit ec: ExecutionContext): Future[Queue[B]] =
      xs.foldLeft(Future.successful(Queue.empty[B])) {
        case (r, x) =>
          for {
            xs <- r
            _ <- GlobalTimer.instance.timer.sleep(10 millis)
            b <- f(x)
          } yield xs.enqueue(b)
      }

  }

}


trait Kafka {

  val containerName = s"kafka-${Random.nextInt(Int.MaxValue)}"
  val networkName = s"waves-${Random.nextInt(Int.MaxValue)}"
  val topicName = "test_topic"

  val network: NetworkImpl =
    Network
      .builder()
      .createNetworkCmdModifier { cmd: CreateNetworkCmd => cmd.withName(networkName) }
      .build()

  protected val kafka: KafkaContainer =
    KafkaContainer("6.1.1").configure { k =>
      k.withNetworkAliases(containerName)
      k.withNetwork(network)
      k.withCreateContainerCmdModifier { cmd =>
        cmd withName containerName
        cmd.getHostConfig.withPortBindings(PortBinding.parse("9092:9092"))
      }
    }

  private def waitForNetworkSettings(pred: NetworkSettings => Boolean): Unit =
    Iterator
      .continually {
        Thread.sleep(1000)
        kafka.dockerClient.inspectContainerCmd(kafka.containerId).exec().getNetworkSettings
      }
      .zipWithIndex
      .find { case (ns, attempt) => pred(ns) || attempt == 10 }
      .fold(println(s"Can't wait on ${kafka.containerId}"))(_ => ())

  protected def disconnectKafkaFromNetwork(): Unit = {
    println("--- Disconnecting Kafka from the network ---")

    kafka.dockerClient
      .disconnectFromNetworkCmd()
      .withContainerId(kafka.containerId)
      .withNetworkId(network.getId)
      .exec()

    waitForNetworkSettings(!_.getNetworks.containsKey(network.getId))

    println("--- Kafka is disconnected from the network ---")
  }

  protected def connectKafkaToNetwork(): Unit = {
    println("--- Connecting Kafka to the network ---")

    kafka.dockerClient
      .connectToNetworkCmd()
      .withContainerId(kafka.containerId)
      .withNetworkId(network.getId)
      .exec()

    waitForNetworkSettings(_.getNetworks.containsKey(network.getId))

    println("--- Kafka is connected to the network ---")
  }

}

class Test extends AnyFlatSpec with Kafka {
  import Implicits.FutureCompanionOps

  import scala.concurrent.ExecutionContext.Implicits.global

  @volatile var lastSent = 0

  def sendMessages(p: KafkaProducer[Null, String], t: String, c: Int) = {
    println("--- Start sending messages to kafka ---")

    val messages = for {i <- 1 to c} yield new ProducerRecord(t, null, i.toString)

    Future.inSeries(messages) { m =>
      val promise = Promise[Unit]()
      p.send(m, (_: RecordMetadata, e: Exception) => Option(e) match {
        case Some(e) => println(s"Message [${m.value()}]: Callback Exception: $e"); promise.success(())
        case None => lastSent += 1; println(s"Message [${m.value()}]: Success"); promise.success(())
      })
      promise.future
    }
  }

  "Kafka" should "not put failed message into the topic" in {
    kafka.start()

    val topicPartition = new TopicPartition(topicName, 0)
    val topicPartitions: util.List[TopicPartition] = java.util.Collections.singletonList(topicPartition)

    var offset = 0

    val producerProps: Properties = {
      val props = new Properties()
      props.put("bootstrap.servers", "localhost:9092")
      val stringSerializerName = classOf[StringSerializer].getName
      props.put("key.serializer", stringSerializerName)
      props.put("value.serializer", stringSerializerName)
      props.put("retries", "0")
      props.put("request.timeout.ms", "100")
      props.put("delivery.timeout.ms", "100")
      props.put("max.in.flight.requests.per.connection", "1")
      props
    }
    val consumerProps: Properties = {
      val props = new Properties()
      props.put("group.id", "test")
      props.put("key.deserializer", classOf[StringDeserializer])
      props.put("value.deserializer", classOf[StringDeserializer])
      props.put("bootstrap.servers", "localhost:9092")
      props
    }

    val producer = new KafkaProducer[Null, String](producerProps)
    val consumer = new KafkaConsumer[Null, String](consumerProps)

    val sm = sendMessages(producer, topicName, 20)

    println("--- Sleep 1 second ---")
    Thread.sleep(1000)

    disconnectKafkaFromNetwork()

    Await.ready(sm, 30 seconds)

    connectKafkaToNetwork()

    consumer.assign(topicPartitions)
    consumer.seekToBeginning(topicPartitions)

    println("--- Start consuming ---")
    while (offset <= lastSent) {
      val results = consumer.poll(2000).asScala
      for (r <- results) {
        println(s"Consumed message: ${r.value()}")
        offset += 1
      }
    }

    producer.close()

    kafka.stop()

    offset shouldBe lastSent
  }

}
