package io.quarkus.sample.superheroes.statistics.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

import io.quarkus.sample.superheroes.statistics.InjectKafkaProducer;
import io.quarkus.sample.superheroes.statistics.KafkaProducerResource;
import io.quarkus.sample.superheroes.statistics.domain.Fight;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for the {@link TopWinnerWebSocket} WebSocket.
 * <p>
 *   These tests use the {@link KafkaProducerResource} to create a {@link KafkaProducer}, injected via {@link InjectKafkaProducer}. The test will publish messages to the Kafka topic while also creating a WebSocket client to listen to messages. Each received message is stored in a {@link BlockingQueue} so that the test can assert the correct messages were produced by the WebSocket.
 * </p>
 */
@QuarkusIntegrationTest
@QuarkusTestResource(KafkaProducerResource.class)
public class TopWinnerWebSocketIT {
	private static final String HERO_NAME = "Chewbacca";
	private static final String HERO_TEAM_NAME = "heroes";
	private static final String VILLAIN_TEAM_NAME = "villains";
	private static final String VILLAIN_NAME = "Darth Vader";

	@TestHTTPResource("/stats/winners")
	URI uri;

	@InjectKafkaProducer
	KafkaProducer<String, Fight> fightsProducer;

	@Test
	public void topWinnerWebSocketTestScenario() throws DeploymentException, IOException, InterruptedException {
		// Set up the Queue to handle the messages
		var messages = new LinkedBlockingQueue<String>();

		// Set up the client to connect to the socket
		try (var session = ContainerProvider.getWebSocketContainer().connectToServer(new Client(messages), this.uri)) {
			// Make sure client connected
			assertThat(messages.poll(5, TimeUnit.MINUTES))
				.isNotNull()
				.isEqualTo("CONNECT");

			// Create 10 fights, split between heroes and villains winning
			var sampleFights = createSampleFights();
			sampleFights.stream()
				.map(fight -> new ProducerRecord<String, Fight>("fights", fight))
				.forEach(this.fightsProducer::send);

			// Wait for our messages to appear in the queue
			await()
				.atMost(Duration.ofMinutes(5))
				.until(() -> messages.size() == sampleFights.size());

			System.out.println("Messages received by test: " + messages);

			// Perform assertions that all expected messages were received
			assertThat(messages.poll())
				.isNotNull()
				.isEqualTo("[%s]", createJsonString(HERO_NAME, 1));

			assertThat(messages.poll())
				.isNotNull()
				.isEqualTo("[%s,%s]", createJsonString(HERO_NAME, 1), createJsonString(VILLAIN_NAME, 1));

			assertThat(messages.poll())
				.isNotNull()
				.isEqualTo("[%s,%s]", createJsonString(HERO_NAME, 2), createJsonString(VILLAIN_NAME, 1));

			assertThat(messages.poll())
				.isNotNull()
				.isEqualTo("[%s,%s]", createJsonString(HERO_NAME, 2), createJsonString(VILLAIN_NAME, 2));

			assertThat(messages.poll())
				.isNotNull()
				.isEqualTo("[%s,%s]", createJsonString(HERO_NAME, 3), createJsonString(VILLAIN_NAME, 2));

			assertThat(messages.poll())
				.isNotNull()
				.isEqualTo("[%s,%s]", createJsonString(HERO_NAME, 3), createJsonString(VILLAIN_NAME, 3));

			assertThat(messages.poll())
				.isNotNull()
				.isEqualTo("[%s,%s]", createJsonString(HERO_NAME, 4), createJsonString(VILLAIN_NAME, 3));

			assertThat(messages.poll())
				.isNotNull()
				.isEqualTo("[%s,%s]", createJsonString(HERO_NAME, 4), createJsonString(VILLAIN_NAME, 4));

			assertThat(messages.poll())
				.isNotNull()
				.isEqualTo("[%s,%s]", createJsonString(HERO_NAME, 5), createJsonString(VILLAIN_NAME, 4));

			assertThat(messages.poll())
				.isNotNull()
				.isEqualTo("[%s,%s]", createJsonString(HERO_NAME, 5), createJsonString(VILLAIN_NAME, 5));
		}
	}

	private static String createJsonString(String name, int score) {
		return String.format("{\"name\":\"%s\",\"score\":%d}", name, score);
	}

	private static List<Fight> createSampleFights() {
		return IntStream.range(0, 10)
			.mapToObj(i -> {
				var heroName = HERO_NAME;
				var villainName = VILLAIN_NAME;
				var fight = Fight.builder();

				if (i % 2 == 0) {
					fight = fight.winnerTeam(HERO_TEAM_NAME)
						.loserTeam(VILLAIN_TEAM_NAME)
						.winnerName(heroName)
						.loserName(villainName);
				}
				else {
					fight = fight.winnerTeam(VILLAIN_TEAM_NAME)
						.loserTeam(HERO_TEAM_NAME)
						.winnerName(villainName)
						.loserName(heroName);
				}

				return fight.build();
			}).collect(Collectors.toList());
	}

	@ClientEndpoint
	private class Client {
		private final BlockingQueue<String> messages;

		private Client(BlockingQueue<String> messages) {
			this.messages = messages;
		}

		@OnOpen
		public void open(Session session) {
			this.messages.add("CONNECT");
		}

		@OnMessage
		void message(String msg) {
			this.messages.add(msg);
		}

		@OnClose
		public void onClose(Session session) {
			this.messages.clear();
		}
	}
}