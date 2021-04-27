package kafka.issue

import com.dimafeng.testcontainers.KafkaContainer
import com.github.dockerjava.api.model.{Network, NetworkSettings, PortBinding}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.{IntegerDeserializer, IntegerSerializer, StringDeserializer, StringSerializer}
import org.scalatest.{BeforeAndAfterAll, Suite}

import java.util.Properties

trait HasKafka extends BeforeAndAfterAll with Logging { this: Suite =>

  protected val kafka: KafkaContainer =
    KafkaContainer("6.1.1").configure { k =>
      k.withCreateContainerCmdModifier { cmd =>
        cmd.getHostConfig.withPortBindings(
          PortBinding.parse("9092:9092"),
          PortBinding.parse("9093:9093"),
          PortBinding.parse("2181:2181"),
        )
      }
    }

  protected lazy val network: Network = kafka.dockerClient.listNetworksCmd().withNameFilter("bridge").exec().get(0)

  private lazy val producerProps: Properties = {
    val props = new Properties()
    props.put("bootstrap.servers", kafka.bootstrapServers)
    props.put("key.serializer", classOf[StringSerializer].getName)
    props.put("value.serializer", classOf[IntegerSerializer].getName)
    props.put("retries", 0)
    props.put("request.timeout.ms", 100)
    props.put("delivery.timeout.ms", 120)
    props.put("max.in.flight.requests.per.connection", 1)
    props
  }

  protected def mkProducer = new KafkaProducer[Null, Int](producerProps)

  private lazy val consumerProps: Properties = {
    val props = new Properties()
    props.put("bootstrap.servers", kafka.bootstrapServers)
    props.put("group.id", "test")
    props.put("key.deserializer", classOf[StringDeserializer].getName)
    props.put("value.deserializer", classOf[IntegerDeserializer].getName)
    //props.put("max-buffer-size", 1000)
    props
  }

  protected def mkConsumer = new KafkaConsumer[Null, Int](consumerProps)

  private def waitForNetworkSettings(pred: NetworkSettings => Boolean): Unit =
    Iterator
      .continually {
        Thread.sleep(1000)
        kafka.dockerClient.inspectContainerCmd(kafka.containerId).exec().getNetworkSettings
      }
      .zipWithIndex
      .find { case (ns, attempt) => pred(ns) || attempt == 10 }
      .fold(log.info(s"Can't wait on ${kafka.containerId}"))(_ => ())

  protected def disconnectKafkaFromNetwork(): Unit = {
    log.info(s"--- Disconnecting Kafka from the network ${network.getName}/${network.getId} ---")

    kafka.dockerClient
      .disconnectFromNetworkCmd()
      .withContainerId(kafka.containerId)
      .withNetworkId(network.getId)
      .exec()

    waitForNetworkSettings(!_.getNetworks.containsKey(network.getId))

    log.info("--- Kafka is disconnected from the network ---")
  }

  protected def connectKafkaToNetwork(): Unit = {
    log.info("--- Connecting Kafka to the network ---")

    kafka.dockerClient
      .connectToNetworkCmd()
      .withContainerId(kafka.containerId)
      .withNetworkId(network.getId)
      .exec()

    waitForNetworkSettings(_.getNetworks.containsKey(network.getId))

    log.info("--- Kafka is connected to the network ---")
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    kafka.start()
  }

  override protected def afterAll(): Unit = {
    kafka.stop()
    super.afterAll()
  }

}
