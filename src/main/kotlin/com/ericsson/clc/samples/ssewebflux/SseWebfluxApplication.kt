package com.ericsson.clc.samples.ssewebflux

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.integration.dsl.*
import org.springframework.integration.file.dsl.Files
import org.springframework.messaging.MessageHandler
import org.springframework.messaging.SubscribableChannel
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.io.File
import java.util.function.Consumer

@SpringBootApplication
class SseWebfluxApplication

fun main(args: Array<String>) {
	runApplication<SseWebfluxApplication>(*args)
}

@Configuration
class IntegrationConfig {

	@Bean
	fun filesChannel(): SubscribableChannel {
		return MessageChannels.publishSubscribe().get()
	}

	@Bean
	fun inboundFlow(@Value("\${input-dir:file://\${HOME}/Desktop/in}") `in`: File): IntegrationFlow {
		val channelAdapterSpec = Files
				.inboundAdapter(`in`)
				.autoCreateDirectory(true)
		val pollerSpec = Consumer<SourcePollingChannelAdapterSpec> { it.poller { spec -> spec.fixedDelay(500) } }
		return IntegrationFlows
				.from(channelAdapterSpec, pollerSpec)
				.transform(Transformers.converter<File, String> { it.absolutePath })
				.channel(filesChannel())
				.get()

	}
}

@RestController
class HomeController {
	@Autowired
	lateinit var filesChannel: SubscribableChannel

	@GetMapping("/files/{name}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
	fun files(@PathVariable name: String): Flux<String> {
		return Flux.create{ sink ->
			val handler = MessageHandler { sink.next(String::class.java.cast(it.payload)) }
			sink.onCancel { filesChannel.unsubscribe(handler) }
			filesChannel.subscribe(handler)
		}
	}
}
